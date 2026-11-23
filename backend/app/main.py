import json
import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Query, Depends, status
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session

from app.database import engine, Base, SessionLocal, get_db
from app.seed import seed_catalog_if_empty
from app.services.auth import decode_access_token
from app.models.safety import UserBlock
from app.websockets.room_manager import focus_room_manager
from app.websockets.safety_manager import safety_manager

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

@asynccontextmanager
async def lifespan(app: FastAPI):
    import os
    if not os.getenv("TESTING"):
        logger.info("Initializing database schema...")
        Base.metadata.create_all(bind=engine)
        with SessionLocal() as session:
            seed_catalog_if_empty(session)
        logger.info("Database schema initialized and catalog seeded.")
    
    yield
    
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
    allow_credentials=True,
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

@app.websocket("/ws/room")
async def focus_room_websocket(
    websocket: WebSocket,
    token: str = Query(...),
    presence_key: Optional[str] = Query(None),
    name: Optional[str] = Query("Cozy Doubler"),
    active_task: Optional[str] = Query("Focusing"),
    completed_tasks: Optional[int] = Query(0),
    total_tasks: Optional[int] = Query(0),
    db: Session = Depends(get_db)
):
    payload = decode_access_token(token)
    if not payload or "sub" not in payload:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    user_id = payload["sub"]
    p_key = presence_key or f"{user_id}:default"

    blocks = db.query(UserBlock).filter(
        (UserBlock.blocker_id == user_id) | (UserBlock.blocked_id == user_id)
    ).all()
    blocked_ids = {
        b.blocked_id if b.blocker_id == user_id else b.blocker_id
        for b in blocks
    }

    initial_presence = {
        "id": p_key,
        "name": name,
        "active_task": active_task,
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

    try:
        while True:
            data = await websocket.receive_text()
            msg = json.loads(data)
            msg_type = msg.get("type")
            if msg_type == "presence_update":
                updated_presence = msg.get("presence", {})
                await focus_room_manager.update_presence(p_key, updated_presence)
            elif msg_type == "ping":
                await websocket.send_text(json.dumps({"type": "pong"}))
    except WebSocketDisconnect as e:
        logger.info(f"WebSocket client disconnected for {p_key}: code={e.code}")
        await focus_room_manager.disconnect(p_key, websocket=websocket)
    except Exception as e:
        logger.warning(f"WebSocket error for {p_key}: {e}")
        await focus_room_manager.disconnect(p_key, websocket=websocket)

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
