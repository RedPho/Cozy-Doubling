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


def test_blocked_profiles_privacy_and_mutual_unblock(client: TestClient):
    alice = create_user_helper(client, "alice_priv@cozy.com", name="Alice")
    bob = create_user_helper(client, "bob_priv@cozy.com", name="Bob")

    # Alice blocks Bob
    assert client.post("/api/safety/block", headers=alice["headers"], json={
        "target_id": bob["profile"]["id"]
    }).status_code == 200

    # Alice sees Bob in her blocked-profiles list, but Bob never sees Alice in his
    alice_blocked = client.get("/api/safety/blocked-profiles", headers=alice["headers"])
    assert alice_blocked.status_code == 200
    assert len(alice_blocked.json()) == 1
    assert alice_blocked.json()[0]["id"] == bob["profile"]["id"]
    assert alice_blocked.json()[0]["display_name"] == "Bob"

    bob_blocked = client.get("/api/safety/blocked-profiles", headers=bob["headers"])
    assert bob_blocked.status_code == 200
    assert bob_blocked.json() == []

    # Now Bob also blocks Alice (mutual block)
    assert client.post("/api/safety/block", headers=bob["headers"], json={
        "target_id": alice["profile"]["id"]
    }).status_code == 200

    # Alice unblocks Bob -> Alice's blocked-profiles becomes empty,
    # BUT bidirectional /api/safety/blocks still includes Bob because Bob still blocks Alice.
    assert client.post("/api/safety/unblock", headers=alice["headers"], json={
        "target_id": bob["profile"]["id"]
    }).status_code == 200

    alice_profiles_after = client.get("/api/safety/blocked-profiles", headers=alice["headers"]).json()
    assert alice_profiles_after == []

    bob_profiles_after = client.get("/api/safety/blocked-profiles", headers=bob["headers"]).json()
    assert len(bob_profiles_after) == 1
    assert bob_profiles_after[0]["id"] == alice["profile"]["id"]

    alice_blocks_after = client.get("/api/safety/blocks", headers=alice["headers"]).json()["blocked_user_ids"]
    assert bob["profile"]["id"] in alice_blocks_after
