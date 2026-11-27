import asyncio
import json
import logging
from contextlib import asynccontextmanager
from typing import Callable, Optional, TypeVar

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Query, Depends, status
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session

from app.config import settings
from app.database import engine, Base, SessionLocal, get_db, ensure_schema_migrations
from app.seed import seed_catalog_if_empty
from app.services.auth import decode_access_token
from app.services.session_lifecycle import (
    auto_finalize_stale_sessions,
    get_verified_active_seconds,
    record_ws_activity,
    record_ws_connect,
    record_ws_disconnect,
)
from app.models.profile import Profile
from app.models.safety import UserBlock
from app.websockets.room_manager import focus_room_manager
from app.websockets.safety_manager import safety_manager
from app.websockets.friends_manager import friends_manager

from app.routers.auth import router as auth_router
from app.routers.profile import router as profile_router
from app.routers.sessions import router as sessions_router
from app.routers.friends import router as friends_router
from app.routers.shop import router as shop_router
from app.routers.inventory import router as inventory_router
from app.routers.safety import router as safety_router
from app.routers.subscriptions import router as subscriptions_router

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("cozy-backend")

async def _stale_session_sweeper():
    while True:
        try:
            await asyncio.sleep(300)
            count = _run_with_db(lambda db: auto_finalize_stale_sessions(db))
            if count:
                logger.info(f"Auto-finalized {count} abandoned focus session(s).")
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.warning(f"Stale session sweep error: {e}")

@asynccontextmanager
async def lifespan(app: FastAPI):
    import os
    sweeper_task: Optional[asyncio.Task] = None
    config_problems = settings.validate()
    if config_problems:
        raise RuntimeError("Refusing to start in production: " + " ".join(config_problems))
    if not settings.is_production:
        logger.warning(
            "ENVIRONMENT=%s: development shortcuts (mock Google tokens, client supporter sync) are enabled.",
            settings.ENVIRONMENT,
        )
    if not os.getenv("TESTING"):
        logger.info("Initializing database schema...")
        Base.metadata.create_all(bind=engine)
        ensure_schema_migrations(engine)
        with SessionLocal() as session:
            seed_catalog_if_empty(session)
        sweeper_task = asyncio.create_task(_stale_session_sweeper())
        logger.info("Database schema initialized and catalog seeded.")
    
    yield

    if sweeper_task is not None:
        sweeper_task.cancel()
        try:
            await sweeper_task
        except asyncio.CancelledError:
            pass
    if not os.getenv("TESTING"):
        engine.dispose()
        logger.info("Database connection closed.")

app = FastAPI(
    title="Cozy Doubling Backend API",
    description="Python backend for Cozy Doubling focusing and body doubling app",
    version="1.0.0",
    lifespan=lifespan
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,  # Bearer tokens only; no cookies to protect
    allow_methods=["*"],
    allow_headers=["*"],
)

# Routers
app.include_router(auth_router)
app.include_router(profile_router)
app.include_router(sessions_router)
app.include_router(friends_router)
app.include_router(shop_router)
app.include_router(inventory_router)
app.include_router(safety_router)
app.include_router(subscriptions_router)

@app.get("/health")
def health_check():
    return {"status": "ok", "app": "cozy-doubling-backend"}

# ── WebSockets ────────────────────────────────────────────────────────────────

T = TypeVar("T")

def _run_with_db(fn: Callable[[Session], T]) -> T:
    db_dep = app.dependency_overrides.get(get_db, get_db)
    gen = db_dep()
    db = next(gen)
    try:
        return fn(db)
    finally:
        gen.close()

def _extract_session_id(presence_key: Optional[str]) -> Optional[str]:
    if not presence_key or ":" not in presence_key:
        return None
    session_id = presence_key.split(":", 1)[1].strip()
    if not session_id or session_id == "default":
        return None
    return session_id

def _canonical_presence_key(user_id: str, presence_key: Optional[str]) -> str:
    raw = (presence_key or "").strip()
    suffix = raw.split(":", 1)[1] if ":" in raw else raw
    suffix = suffix.strip()[:64] or "default"
    return f"{user_id}:{suffix}"

def _fetch_display_name(db: Session, user_id: str) -> Optional[str]:
    profile = db.query(Profile).filter(Profile.id == user_id).first()
    if profile is None:
        return None
    return (profile.display_name or "Cozy Doubler").strip()[:30] or "Cozy Doubler"

def _fetch_blocked_ids(user_id: str) -> set[str]:
    def _query(db: Session) -> set[str]:
        blocks = db.query(UserBlock).filter(
            (UserBlock.blocker_id == user_id) | (UserBlock.blocked_id == user_id)
        ).all()
        return {
            b.blocked_id if b.blocker_id == user_id else b.blocker_id
            for b in blocks
        }
    return _run_with_db(_query)

@app.websocket("/ws/room")
async def focus_room_websocket(
    websocket: WebSocket,
    token: str = Query(...),
    presence_key: Optional[str] = Query(None),
    name: Optional[str] = Query("Cozy Doubler"),
    active_task: Optional[str] = Query("Focusing"),
    completed_tasks: Optional[int] = Query(0),
    total_tasks: Optional[int] = Query(0),
):
    payload = decode_access_token(token)
    if not payload or "sub" not in payload:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    user_id = payload["sub"]
    # The presence key is always namespaced by the *authenticated* user. A client-chosen key such
    # as "<victim_id>:x" would otherwise replace the victim's socket and make blocks target them.
    p_key = _canonical_presence_key(user_id, presence_key)
    session_id = _extract_session_id(p_key)
    profile_name = _run_with_db(lambda db: _fetch_display_name(db, user_id))
    if profile_name is None:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    connect_status = _run_with_db(lambda db: record_ws_connect(db, user_id, session_id))
    if connect_status in ("expired", "already_processed"):
        await websocket.accept()
        await websocket.send_text(json.dumps({
            "type": "session_expired",
            "session_id": session_id,
            "reason": "abandoned_disconnect"
        }))
        await websocket.close(code=4001, reason="Session expired")
        return

    blocked_ids = _fetch_blocked_ids(user_id)

    # Display name comes from the server-side profile, never from the client (anti-impersonation).
    safe_name = profile_name
    safe_task = (active_task or "Focusing").strip()[:120] or "Focusing"
    initial_presence = {
        "id": p_key,
        "name": safe_name,
        "active_task": safe_task,
        "completed_tasks": completed_tasks,
        "total_tasks": total_tasks
    }

    connected = await focus_room_manager.connect(
        presence_key=p_key,
        user_id=user_id,
        websocket=websocket,
        initial_presence=initial_presence,
        user_blocked_ids=blocked_ids
    )

    if not connected:
        return

    if session_id is not None:
        active_secs = _run_with_db(
            lambda db: get_verified_active_seconds(db, user_id, session_id)
        )
        if active_secs is not None:
            await websocket.send_text(json.dumps({
                "type": "session_sync",
                "session_id": session_id,
                "active_seconds": active_secs,
            }))

    try:
        while True:
            data = await websocket.receive_text()
            try:
                msg = json.loads(data)
            except json.JSONDecodeError as e:
                logger.warning(f"Malformed JSON from {p_key}: {e}")
                continue
            msg_type = msg.get("type")
            if msg_type == "presence_update":
                raw_presence = msg.get("presence")
                updated_presence = dict(raw_presence) if isinstance(raw_presence, dict) else {}
                updated_presence["name"] = safe_name
                if isinstance(updated_presence.get("active_task"), str):
                    updated_presence["active_task"] = updated_presence["active_task"].strip()[:120]
                await focus_room_manager.update_presence(p_key, updated_presence)
                act_status = _run_with_db(
                    lambda db: record_ws_activity(
                        db,
                        user_id=user_id,
                        session_id=session_id,
                        tasks_completed=updated_presence.get("completed_tasks"),
                        active_task_text=updated_presence.get("active_task"),
                    )
                )
                if act_status == "capped":
                    await websocket.send_text(json.dumps({
                        "type": "session_expired",
                        "session_id": session_id,
                        "reason": "capped_max_duration"
                    }))
                    await focus_room_manager.disconnect(p_key, websocket=websocket)
                    await websocket.close(code=4002, reason="Max duration reached")
                    return
            elif msg_type == "ping":
                act_status = _run_with_db(
                    lambda db: record_ws_activity(
                        db,
                        user_id=user_id,
                        session_id=session_id,
                    )
                )
                if act_status == "capped":
                    await websocket.send_text(json.dumps({
                        "type": "session_expired",
                        "session_id": session_id,
                        "reason": "capped_max_duration"
                    }))
                    await focus_room_manager.disconnect(p_key, websocket=websocket)
                    await websocket.close(code=4002, reason="Max duration reached")
                    return
                if session_id is not None:
                    active_secs = _run_with_db(
                        lambda db: get_verified_active_seconds(db, user_id, session_id)
                    )
                    pong_payload = {
                        "type": "pong",
                        "session_id": session_id,
                        "active_seconds": active_secs,
                    } if active_secs is not None else {"type": "pong"}
                else:
                    pong_payload = {"type": "pong"}
                await websocket.send_text(json.dumps(pong_payload))
    except WebSocketDisconnect as e:
        logger.info(f"WebSocket client disconnected for {p_key}: code={e.code}")
        removed = await focus_room_manager.disconnect(p_key, websocket=websocket)
        if removed:
            _run_with_db(lambda db: record_ws_disconnect(db, user_id, session_id))
    except Exception as e:
        logger.warning(f"WebSocket error for {p_key}: {e}")
        removed = await focus_room_manager.disconnect(p_key, websocket=websocket)
        if removed:
            _run_with_db(lambda db: record_ws_disconnect(db, user_id, session_id))

@app.websocket("/ws/safety")
async def safety_websocket(
    websocket: WebSocket,
    token: str = Query(...)
):
    payload = decode_access_token(token)
    if not payload or "sub" not in payload:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    user_id = payload["sub"]
    await safety_manager.connect(user_id, websocket)

    try:
        while True:
            data = await websocket.receive_text()
            msg = json.loads(data)
            if msg.get("type") == "ping":
                await websocket.send_text(json.dumps({"type": "pong"}))
    except WebSocketDisconnect:
        safety_manager.disconnect(user_id, websocket)
    except Exception:
        safety_manager.disconnect(user_id, websocket)


@app.websocket("/ws/friends")
async def friends_websocket(
    websocket: WebSocket,
    token: str = Query(...)
):
    payload = decode_access_token(token)
    if not payload or "sub" not in payload:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    user_id = payload["sub"]
    await friends_manager.connect(user_id, websocket)

    try:
        while True:
            data = await websocket.receive_text()
            msg = json.loads(data)
            if msg.get("type") == "ping":
                await websocket.send_text(json.dumps({"type": "pong"}))
    except WebSocketDisconnect:
        friends_manager.disconnect(user_id, websocket)
    except Exception:
        friends_manager.disconnect(user_id, websocket)
