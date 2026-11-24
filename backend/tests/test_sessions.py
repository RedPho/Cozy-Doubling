from starlette.testclient import TestClient
from tests.conftest import create_user_helper

def test_session_lifecycle_and_leaf_rewards(client: TestClient):
    user = create_user_helper(client, "focuser@cozy.com")
    headers = user["headers"]

    # Start session
    start_resp = client.post("/api/sessions/start", headers=headers)
    assert start_resp.status_code == 200
    session_id = start_resp.json()["session_id"]
    assert session_id is not None

    # Finish session with 3 tasks completed
    finish_resp = client.post(f"/api/sessions/{session_id}/finish", headers=headers, json={
        "tasks_completed": 3,
        "last_task_text": "Completed task 3"
    })
    assert finish_resp.status_code == 200
    session = finish_resp.json()
    assert session["tasks_completed"] == 3
    assert session["is_processed"] is True
    # Anti-cheat formula: earned = max(1, duration*1 + tasks*3) => 0*1 + 3*3 = 9 leaves
    assert session["earned_leaves"] == 9
    assert session["last_task_text"] == "Completed task 3"

    # Verify profile leaves balance incremented
    profile_resp = client.get("/api/profile", headers=headers)
    assert profile_resp.json()["leaves"] == 9

    # Re-finishing an already finalized session must fail
    refinish_resp = client.post(f"/api/sessions/{session_id}/finish", headers=headers, json={
        "tasks_completed": 3,
        "last_task_text": "Completed task 3"
    })
    assert refinish_resp.status_code == 400
    assert "already processed" in refinish_resp.json()["detail"].lower()

def test_session_minimum_reward_floor(client: TestClient):
    user = create_user_helper(client, "quick_focuser@cozy.com")
    headers = user["headers"]

    start_resp = client.post("/api/sessions/start", headers=headers)
    session_id = start_resp.json()["session_id"]

    finish_resp = client.post(f"/api/sessions/{session_id}/finish", headers=headers, json={
        "tasks_completed": 0,
        "last_task_text": ""
    })
    assert finish_resp.status_code == 200
    assert finish_resp.json()["earned_leaves"] == 1
