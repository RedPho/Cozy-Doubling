from starlette.testclient import TestClient
from tests.conftest import create_user_helper

def test_blocking_and_reporting_flow(client: TestClient):
    alice = create_user_helper(client, "alice_safe@cozy.com", name="Alice")
    bob = create_user_helper(client, "bob_safe@cozy.com", name="Bob")

    # Alice blocks Bob
    block_resp = client.post("/api/safety/block", headers=alice["headers"], json={
        "target_id": bob["profile"]["id"]
    })
    assert block_resp.status_code == 200

    # Check blocked list
    blocks_resp = client.get("/api/safety/blocks", headers=alice["headers"])
    assert blocks_resp.status_code == 200
    assert bob["profile"]["id"] in blocks_resp.json()["blocked_user_ids"]

    # Bob tries to add Alice
    client.post("/api/friends/request", headers=bob["headers"], json={
        "player_tag": alice["profile"]["player_tag"]
    })
    # Alice checks incoming requests -> Bob must not appear
    alice_reqs = client.get("/api/friends/requests", headers=alice["headers"])
    assert len(alice_reqs.json()) == 0

    # Unblock
    unblock_resp = client.post("/api/safety/unblock", headers=alice["headers"], json={
        "target_id": bob["profile"]["id"]
    })
    assert unblock_resp.status_code == 200

    # Report user
    report_resp = client.post("/api/safety/report", headers=alice["headers"], json={
        "target_id": bob["profile"]["id"],
        "reason": "Inappropriate active task description"
    })
    assert report_resp.status_code == 200
    assert "submitted" in report_resp.json()["message"].lower()

    # Empty report reason fails
    empty_report = client.post("/api/safety/report", headers=alice["headers"], json={
        "target_id": bob["profile"]["id"],
        "reason": "   "
    })
    assert empty_report.status_code == 400
