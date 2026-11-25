import json
import time
from starlette.testclient import TestClient
from app.services.auth import create_access_token
from app.websockets.room_manager import focus_room_manager
from app.websockets.safety_manager import safety_manager
from app.config import settings

def test_two_clients_full_presence_lifecycle(client: TestClient):
    """
    Simulates exactly two real users (e.g. Emin and Osman) entering the focus room,
    exchanging presence, updating task state in real time, and cleanly leaving.
    """
    focus_room_manager.clear()

    token1 = create_access_token({"sub": "user_emin_01", "email": "emin@cozy.com"})
    token2 = create_access_token({"sub": "user_osman_02", "email": "osman@cozy.com"})

    # 1. Emin connects
    with client.websocket_connect(
        f"/ws/room?token={token1}&presence_key=user_emin_01:sess1&name=emin&active_task=Coding&completed_tasks=1&total_tasks=3"
    ) as ws1:
        msg1 = json.loads(ws1.receive_text())
        assert msg1["type"] == "presence_state"
        assert len(msg1["participants"]) == 0, "First participant should see empty other participants list"

        # 2. Osman connects while Emin is already in the room
        with client.websocket_connect(
            f"/ws/room?token={token2}&presence_key=user_osman_02:sess2&name=osman&active_task=Studying&completed_tasks=0&total_tasks=2"
        ) as ws2:
            # Osman should immediately receive presence_state containing Emin
            msg2 = json.loads(ws2.receive_text())
            assert msg2["type"] == "presence_state"
            assert len(msg2["participants"]) == 1
            emin_in_state = msg2["participants"][0]
            assert emin_in_state["id"] == "user_emin_01:sess1"
            assert emin_in_state["name"] == "emin"
            assert emin_in_state["active_task"] == "Coding"

            # Emin should immediately receive broadcast presence_update containing Osman
            update_for_emin = json.loads(ws1.receive_text())
            assert update_for_emin["type"] == "presence_update"
            osman_presence = update_for_emin["presence"]
            assert osman_presence["id"] == "user_osman_02:sess2"
            assert osman_presence["name"] == "osman"
            assert osman_presence["active_task"] == "Studying"

            # 3. Osman updates task state -> Emin must receive it
            ws2.send_text(json.dumps({
                "type": "presence_update",
                "presence": {
                    "id": "user_osman_02:sess2",
                    "name": "osman",
                    "active_task": "Finished chapter 1",
                    "completed_tasks": 1,
                    "total_tasks": 2
                }
            }))
            time.sleep(0.05)
            update_received_by_emin = json.loads(ws1.receive_text())
            assert update_received_by_emin["type"] == "presence_update"
            assert update_received_by_emin["presence"]["active_task"] == "Finished chapter 1"
            assert update_received_by_emin["presence"]["completed_tasks"] == 1

            # 4. Emin updates task state -> Osman must receive it
            ws1.send_text(json.dumps({
                "type": "presence_update",
                "presence": {
                    "id": "user_emin_01:sess1",
                    "name": "emin",
                    "active_task": "Running backend tests",
                    "completed_tasks": 2,
                    "total_tasks": 3
                }
            }))
            time.sleep(0.05)
            update_received_by_osman = json.loads(ws2.receive_text())
            assert update_received_by_osman["type"] == "presence_update"
            assert update_received_by_osman["presence"]["active_task"] == "Running backend tests"
            assert update_received_by_osman["presence"]["completed_tasks"] == 2

            # Osman closes socket
            ws2.close()

        # 5. Osman leaves -> Emin must receive presence_leave
        time.sleep(0.05)
        leave_msg = json.loads(ws1.receive_text())
        assert leave_msg["type"] == "presence_leave"
        assert leave_msg["presence_id"] == "user_osman_02:sess2"

def test_three_concurrent_clients_presence(client: TestClient):
    """
    Tests 3 concurrent users joining and verifying broadcast distribution.
    """
    focus_room_manager.clear()

    t1 = create_access_token({"sub": "u1", "email": "u1@cozy.com"})
    t2 = create_access_token({"sub": "u2", "email": "u2@cozy.com"})
    t3 = create_access_token({"sub": "u3", "email": "u3@cozy.com"})

    with client.websocket_connect(f"/ws/room?token={t1}&presence_key=u1:s1&name=User1") as ws1:
        _ = ws1.receive_text()  # state 0

        with client.websocket_connect(f"/ws/room?token={t2}&presence_key=u2:s2&name=User2") as ws2:
            _ = ws2.receive_text()  # state 1
            _ = ws1.receive_text()  # update for u2

            with client.websocket_connect(f"/ws/room?token={t3}&presence_key=u3:s3&name=User3") as ws3:
                # u3 should see both u1 and u2 in initial presence_state
                msg3 = json.loads(ws3.receive_text())
                assert len(msg3["participants"]) == 2
                ids = {p["id"] for p in msg3["participants"]}
                assert ids == {"u1:s1", "u2:s2"}

                # both ws1 and ws2 receive update for u3
                up1 = json.loads(ws1.receive_text())
                up2 = json.loads(ws2.receive_text())
                assert up1["presence"]["id"] == "u3:s3"
                assert up2["presence"]["id"] == "u3:s3"

                ws3.close()

            time.sleep(0.05)
            # u3 leaves -> ws1 and ws2 receive presence_leave
            leave1 = json.loads(ws1.receive_text())
            leave2 = json.loads(ws2.receive_text())
            assert leave1["type"] == "presence_leave" and leave1["presence_id"] == "u3:s3"
            assert leave2["type"] == "presence_leave" and leave2["presence_id"] == "u3:s3"

def test_safety_blocking_filters_presence_bidirectionally(client: TestClient):
    """
    If User A blocks User B, they must NOT see each other in presence_state
    or receive each other's broadcasts.
    """
    focus_room_manager.clear()

    # Register both users in DB
    reg_a = client.post("/api/auth/register", json={"email": "blocker_a@test.com", "password": "Password123!"})
    token_a = reg_a.json()["access_token"]
    user_a_id = client.get("/api/auth/me", headers={"Authorization": f"Bearer {token_a}"}).json()["id"]

    reg_b = client.post("/api/auth/register", json={"email": "blocked_b@test.com", "password": "Password123!"})
    token_b = reg_b.json()["access_token"]
    user_b_id = client.get("/api/auth/me", headers={"Authorization": f"Bearer {token_b}"}).json()["id"]

    # User A blocks User B
    client.post("/api/safety/block", json={"target_id": user_b_id}, headers={"Authorization": f"Bearer {token_a}"})

    with client.websocket_connect(f"/ws/room?token={token_a}&presence_key={user_a_id}:sA&name=BlockerA") as ws_a:
        _ = ws_a.receive_text()

        with client.websocket_connect(f"/ws/room?token={token_b}&presence_key={user_b_id}:sB&name=BlockedB") as ws_b:
            msg_b = json.loads(ws_b.receive_text())
            # Blocked B must NOT see Blocker A in presence_state
            assert len(msg_b["participants"]) == 0

            # B updates presence -> A must NOT receive it
            ws_b.send_text(json.dumps({
                "type": "presence_update",
                "presence": {"id": f"{user_b_id}:sB", "name": "BlockedB", "active_task": "Secret"}
            }))

            time.sleep(0.05)
            # Send ping to ws_a to verify its queue has no leaked broadcasts
            ws_a.send_text(json.dumps({"type": "ping"}))
            resp_a = json.loads(ws_a.receive_text())
            assert resp_a["type"] == "pong", "Blocker A should have received pong without seeing Blocked B update"

def test_realtime_block_separates_room_participants(client: TestClient):
    """
    If User A and User B are both in the room, and User A blocks User B via REST API,
    both users must immediately receive presence_leave for the other user.
    """
    focus_room_manager.clear()

    reg_a = client.post("/api/auth/register", json={"email": "dyn_a@test.com", "password": "Password123!"})
    token_a = reg_a.json()["access_token"]
    user_a_id = client.get("/api/auth/me", headers={"Authorization": f"Bearer {token_a}"}).json()["id"]

    reg_b = client.post("/api/auth/register", json={"email": "dyn_b@test.com", "password": "Password123!"})
    token_b = reg_b.json()["access_token"]
    user_b_id = client.get("/api/auth/me", headers={"Authorization": f"Bearer {token_b}"}).json()["id"]

    with client.websocket_connect(f"/ws/room?token={token_a}&presence_key={user_a_id}:sA&name=UserA") as ws_a:
        _ = ws_a.receive_text()
        with client.websocket_connect(f"/ws/room?token={token_b}&presence_key={user_b_id}:sB&name=UserB") as ws_b:
            _ = ws_b.receive_text()
            _ = ws_a.receive_text()  # ws_a sees ws_b join

            # User A blocks User B
            client.post("/api/safety/block", json={"target_id": user_b_id}, headers={"Authorization": f"Bearer {token_a}"})
            time.sleep(0.05)

            # Both ws_a and ws_b should immediately receive presence_leave for each other
            leave_for_a = json.loads(ws_a.receive_text())
            assert leave_for_a["type"] == "presence_leave"
            assert leave_for_a["presence_id"] == f"{user_b_id}:sB"

            leave_for_b = json.loads(ws_b.receive_text())
            assert leave_for_b["type"] == "presence_leave"
            assert leave_for_b["presence_id"] == f"{user_a_id}:sA"

            # User A unblocks User B -> both immediately receive presence_update for each other
            client.post("/api/safety/unblock", json={"target_id": user_b_id}, headers={"Authorization": f"Bearer {token_a}"})
            time.sleep(0.05)

            restore_for_a = json.loads(ws_a.receive_text())
            assert restore_for_a["type"] == "presence_update"
            assert restore_for_a["presence"]["id"] == f"{user_b_id}:sB"

            restore_for_b = json.loads(ws_b.receive_text())
            assert restore_for_b["type"] == "presence_update"
            assert restore_for_b["presence"]["id"] == f"{user_a_id}:sA"

def test_reconnect_replaces_old_socket_cleanly(client: TestClient):
    """
    If a client reconnects with the same presence_key, the new socket replaces
    the old one without ghost participants or premature disconnects.
    """
    focus_room_manager.clear()

    token = create_access_token({"sub": "u_reconnect", "email": "rec@cozy.com"})
    p_key = "u_reconnect:sess_active"

    with client.websocket_connect(f"/ws/room?token={token}&presence_key={p_key}&name=RecUser") as ws_old:
        _ = ws_old.receive_text()
        assert len(focus_room_manager.active_participants) == 1
        old_server_ws = focus_room_manager.active_participants[p_key].websocket
        assert focus_room_manager.active_participants[p_key].presence_data["name"] == "RecUser"

        # Reconnect with same presence_key
        with client.websocket_connect(f"/ws/room?token={token}&presence_key={p_key}&name=RecUserReconnected") as ws_new:
            _ = ws_new.receive_text()
            assert len(focus_room_manager.active_participants) == 1
            # Current entry must be updated with the new server websocket and name
            new_server_ws = focus_room_manager.active_participants[p_key].websocket
            assert new_server_ws != old_server_ws
            assert focus_room_manager.active_participants[p_key].presence_data["name"] == "RecUserReconnected"

def test_room_capacity_limit_rejection(client: TestClient):
    """
    If room reaches capacity limit, additional connections are rejected with 1008.
    """
    focus_room_manager.clear()
    original_limit = settings.ROOM_CAPACITY_LIMIT
    settings.ROOM_CAPACITY_LIMIT = 2

    try:
        t1 = create_access_token({"sub": "cap1", "email": "cap1@cozy.com"})
        t2 = create_access_token({"sub": "cap2", "email": "cap2@cozy.com"})
        t3 = create_access_token({"sub": "cap3", "email": "cap3@cozy.com"})

        with client.websocket_connect(f"/ws/room?token={t1}&presence_key=cap1:s&name=C1"):
            with client.websocket_connect(f"/ws/room?token={t2}&presence_key=cap2:s&name=C2"):
                try:
                    with client.websocket_connect(f"/ws/room?token={t3}&presence_key=cap3:s&name=C3"):
                        assert False, "Should have been rejected due to capacity"
                except Exception:
                    # Expected rejection
                    pass
    finally:
        settings.ROOM_CAPACITY_LIMIT = original_limit

def test_invalid_token_rejected_on_connect(client: TestClient):
    """
    Invalid token must be rejected with 1008 immediately.
    """
    try:
        with client.websocket_connect("/ws/room?token=invalid_garbage_token"):
            assert False, "Invalid token should be closed"
    except Exception:
        pass

def test_safety_websocket_realtime_events(client: TestClient):
    """
    Verify /ws/safety receives block_added and block_removed events in real time.
    """
    safety_manager.clear()

    reg_a = client.post("/api/auth/register", json={"email": "safe_a@test.com", "password": "Password123!"})
    token_a = reg_a.json()["access_token"]
    user_a_id = client.get("/api/auth/me", headers={"Authorization": f"Bearer {token_a}"}).json()["id"]

    reg_b = client.post("/api/auth/register", json={"email": "safe_b@test.com", "password": "Password123!"})
    token_b = reg_b.json()["access_token"]
    user_b_id = client.get("/api/auth/me", headers={"Authorization": f"Bearer {token_b}"}).json()["id"]

    with client.websocket_connect(f"/ws/safety?token={token_a}") as ws_safe_a:
        with client.websocket_connect(f"/ws/safety?token={token_b}") as ws_safe_b:
            # Ping test
            ws_safe_a.send_text(json.dumps({"type": "ping"}))
            pong = json.loads(ws_safe_a.receive_text())
            assert pong["type"] == "pong"

            # Block B
            client.post("/api/safety/block", json={"target_id": user_b_id}, headers={"Authorization": f"Bearer {token_a}"})
            time.sleep(0.05)
            msg_a = json.loads(ws_safe_a.receive_text())
            assert msg_a["type"] == "block_added" and msg_a["blocked_id"] == user_b_id

            msg_b = json.loads(ws_safe_b.receive_text())
            assert msg_b["type"] == "block_added" and msg_b["blocked_id"] == user_a_id

            # Unblock B
            client.post("/api/safety/unblock", json={"target_id": user_b_id}, headers={"Authorization": f"Bearer {token_a}"})
            time.sleep(0.05)
            msg_unblock_a = json.loads(ws_safe_a.receive_text())
            assert msg_unblock_a["type"] == "block_removed" and msg_unblock_a["blocked_id"] == user_b_id

            msg_unblock_b = json.loads(ws_safe_b.receive_text())
            assert msg_unblock_b["type"] == "block_removed" and msg_unblock_b["blocked_id"] == user_a_id

def test_two_clients_same_account_distinct_sessions(client: TestClient):
    """
    Simulates testing with two devices on the same account (or identical mock tokens).
    Both sessions must be kept distinct in the room and receive each other's updates.
    """
    focus_room_manager.clear()

    token = create_access_token({"sub": "shared_user_01", "email": "shared@cozy.com"})

    with client.websocket_connect(
        f"/ws/room?token={token}&presence_key=shared_user_01:device_1_sess&name=Device1"
    ) as ws1:
        msg1 = json.loads(ws1.receive_text())
        assert len(msg1["participants"]) == 0

        with client.websocket_connect(
            f"/ws/room?token={token}&presence_key=shared_user_01:device_2_sess&name=Device2"
        ) as ws2:
            # Device 2 should receive presence_state with Device 1
            msg2 = json.loads(ws2.receive_text())
            assert len(msg2["participants"]) == 1
            assert msg2["participants"][0]["id"] == "shared_user_01:device_1_sess"

            # Device 1 should receive presence_update with Device 2
            up1 = json.loads(ws1.receive_text())
            assert up1["type"] == "presence_update"
            assert up1["presence"]["id"] == "shared_user_01:device_2_sess"
            assert up1["presence"]["name"] == "Device2"
