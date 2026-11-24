import asyncio
import json
import socket
import urllib.request

import uvicorn
import websockets

from app.main import app
from app.services.auth import create_access_token
from app.websockets.room_manager import focus_room_manager


def _get_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def _http_get_json(url: str, headers: dict[str, str] | None = None) -> tuple[int, dict]:
    req = urllib.request.Request(url, headers=headers or {}, method="GET")
    with urllib.request.urlopen(req, timeout=5) as resp:
        return resp.status, json.loads(resp.read().decode("utf-8"))


async def _run_two_clients_bidirectional_and_keepalive():
    focus_room_manager.clear()
    port = _get_free_port()
    config = uvicorn.Config(
        app,
        host="127.0.0.1",
        port=port,
        log_level="warning",
        ws_ping_interval=1.5,
        ws_ping_timeout=1.5,
    )
    server = uvicorn.Server(config)
    server.install_signal_handlers = lambda: None
    server_task = asyncio.create_task(server.serve())

    try:
        while not server.started:
            await asyncio.sleep(0.05)

        t1 = create_access_token({"sub": "live_u1", "email": "osman_live@cozy.com"})
        t2 = create_access_token({"sub": "live_u2", "email": "emin_live@cozy.com"})
        url1 = f"ws://127.0.0.1:{port}/ws/room?token={t1}&presence_key=live_u1:s1&name=Osman"
        url2 = f"ws://127.0.0.1:{port}/ws/room?token={t2}&presence_key=live_u2:s2&name=Emin"

        async with websockets.connect(url1, ping_interval=1.0, ping_timeout=2.0) as ws1:
            msg1 = json.loads(await asyncio.wait_for(ws1.recv(), timeout=3.0))
            assert msg1 == {"type": "presence_state", "participants": []}

            await ws1.send(json.dumps({
                "type": "presence_update",
                "presence": {
                    "id": "live_u1:s1",
                    "name": "Osman",
                    "active_task": "Reading docs",
                    "completed_tasks": 1,
                    "total_tasks": 3,
                },
            }))
            await asyncio.sleep(0.1)

            async with websockets.connect(url2, ping_interval=1.0, ping_timeout=2.0) as ws2:
                msg2 = json.loads(await asyncio.wait_for(ws2.recv(), timeout=3.0))
                assert msg2["type"] == "presence_state"
                assert len(msg2["participants"]) == 1
                assert msg2["participants"][0]["id"] == "live_u1:s1"
                assert msg2["participants"][0]["name"] == "Osman"
                assert msg2["participants"][0]["active_task"] == "Reading docs"

                join_for_ws1 = json.loads(await asyncio.wait_for(ws1.recv(), timeout=3.0))
                assert join_for_ws1["type"] == "presence_update"
                assert join_for_ws1["presence"]["id"] == "live_u2:s2"
                assert join_for_ws1["presence"]["name"] == "Emin"

                # Hold open across multiple server+client ping/pong keepalive cycles
                await asyncio.sleep(3.5)

                await ws2.send(json.dumps({
                    "type": "presence_update",
                    "presence": {
                        "id": "live_u2:s2",
                        "name": "Emin",
                        "active_task": "Writing isolated tests",
                        "completed_tasks": 2,
                        "total_tasks": 2,
                    },
                }))
                update_for_ws1 = json.loads(await asyncio.wait_for(ws1.recv(), timeout=3.0))
                assert update_for_ws1["type"] == "presence_update"
                assert update_for_ws1["presence"]["active_task"] == "Writing isolated tests"
                assert update_for_ws1["presence"]["completed_tasks"] == 2

                await ws2.close()

            leave_for_ws1 = json.loads(await asyncio.wait_for(ws1.recv(), timeout=3.0))
            assert leave_for_ws1["type"] == "presence_leave"
            assert leave_for_ws1["presence_id"] == "live_u2:s2"
    finally:
        server.should_exit = True
        await server_task
        focus_room_manager.clear()


async def _run_many_websockets_do_not_exhaust_db_pool(token: str, user_id: str):
    focus_room_manager.clear()
    port = _get_free_port()
    config = uvicorn.Config(
        app,
        host="127.0.0.1",
        port=port,
        log_level="warning",
        ws_ping_interval=25.0,
        ws_ping_timeout=25.0,
    )
    server = uvicorn.Server(config)
    server.install_signal_handlers = lambda: None
    server_task = asyncio.create_task(server.serve())

    sockets = []
    try:
        while not server.started:
            await asyncio.sleep(0.05)

        # Open 20 simultaneous WebSockets (exceeds default SQLAlchemy QueuePool size 5 + overflow 10 = 15)
        for i in range(20):
            ws = await websockets.connect(
                f"ws://127.0.0.1:{port}/ws/room?token={token}&presence_key={user_id}:pool_{i}&name=User{i}"
            )
            initial = json.loads(await asyncio.wait_for(ws.recv(), timeout=3.0))
            assert initial["type"] == "presence_state"
            sockets.append(ws)

        # While all 20 WebSockets remain open, HTTP DB queries and a 21st WebSocket must succeed without blocking
        loop = asyncio.get_running_loop()
        status_health, body_health = await loop.run_in_executor(
            None, _http_get_json, f"http://127.0.0.1:{port}/health"
        )
        assert status_health == 200 and body_health["status"] == "ok"

        status_prof, body_prof = await loop.run_in_executor(
            None,
            _http_get_json,
            f"http://127.0.0.1:{port}/api/profile",
            {"Authorization": f"Bearer {token}"},
        )
        assert status_prof == 200 and body_prof["id"] == user_id

        ws21 = await websockets.connect(
            f"ws://127.0.0.1:{port}/ws/room?token={token}&presence_key={user_id}:pool_20&name=User20"
        )
        sockets.append(ws21)
        state21 = json.loads(await asyncio.wait_for(ws21.recv(), timeout=3.0))
        assert len(state21["participants"]) == 20
    finally:
        await asyncio.gather(*(ws.close() for ws in sockets), return_exceptions=True)
        server.should_exit = True
        await server_task
        focus_room_manager.clear()


def test_live_uvicorn_two_clients_bidirectional_presence_and_keepalive(client=None):
    asyncio.run(_run_two_clients_bidirectional_and_keepalive())


def test_live_uvicorn_many_websockets_do_not_exhaust_db_pool(client):
    reg = client.post(
        "/api/auth/register",
        json={"email": "pooltest@cozy.com", "password": "Password123!", "display_name": "PoolTester"},
    )
    assert reg.status_code == 200
    token = reg.json()["access_token"]
    me = client.get("/api/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me.status_code == 200
    user_id = me.json()["id"]

    asyncio.run(_run_many_websockets_do_not_exhaust_db_pool(token, user_id))
