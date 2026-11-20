import json
from starlette.testclient import TestClient
from app.services.auth import create_access_token

def test_focus_room_websocket_presence(client: TestClient):
    token = create_access_token({"sub": "ws_test_user_01", "email": "ws@cozy.com"})
    
    with client.websocket_connect(
        f"/ws/room?token={token}&presence_key=ws_test_user_01:session1&name=WebSocketTester&active_task=Testing&completed_tasks=1&total_tasks=3"
    ) as websocket:
        initial_msg = websocket.receive_text()
        data = json.loads(initial_msg)
        assert data["type"] == "presence_state"

        websocket.send_text(json.dumps({"type": "ping"}))
        pong_msg = websocket.receive_text()
        pong_data = json.loads(pong_msg)
        assert pong_data["type"] == "pong"

        websocket.send_text(json.dumps({
            "type": "presence_update",
            "presence": {
                "id": "ws_test_user_01:session1",
                "name": "WebSocketTester",
                "active_task": "Updated Task",
                "completed_tasks": 2,
                "total_tasks": 3
            }
        }))

def test_safety_websocket(client: TestClient):
    token = create_access_token({"sub": "ws_safety_user_01", "email": "safety@cozy.com"})
    
    with client.websocket_connect(f"/ws/safety?token={token}") as websocket:
        websocket.send_text(json.dumps({"type": "ping"}))
        pong_msg = websocket.receive_text()
        pong_data = json.loads(pong_msg)
        assert pong_data["type"] == "pong"
