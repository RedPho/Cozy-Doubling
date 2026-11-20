from starlette.testclient import TestClient
from tests.conftest import create_user_helper

def test_friend_request_flow(client: TestClient):
    alice = create_user_helper(client, "alice@cozy.com", name="Alice")
    bob = create_user_helper(client, "bob@cozy.com", name="Bob")

    # Cannot add self
    self_resp = client.post("/api/friends/request", headers=alice["headers"], json={
        "player_tag": alice["profile"]["player_tag"]
    })
    assert self_resp.status_code == 400
    assert "add yourself" in self_resp.json()["detail"].lower()

    # Alice sends request to Bob
    req_resp = client.post("/api/friends/request", headers=alice["headers"], json={
        "player_tag": bob["profile"]["player_tag"]
    })
    assert req_resp.status_code == 200

    # Bob sees pending request
    bob_pending = client.get("/api/friends/requests", headers=bob["headers"])
    assert bob_pending.status_code == 200
    pending_list = bob_pending.json()
    assert len(pending_list) == 1
    assert pending_list[0]["id"] == alice["profile"]["id"]

    # Bob accepts Alice's request
    accept_resp = client.post(f"/api/friends/{alice['profile']['id']}/accept", headers=bob["headers"])
    assert accept_resp.status_code == 200

    # Both see each other in friends list
    bob_friends = client.get("/api/friends", headers=bob["headers"])
    assert len(bob_friends.json()) == 1
    assert bob_friends.json()[0]["friend_id"] == alice["profile"]["id"]

    alice_friends = client.get("/api/friends", headers=alice["headers"])
    assert len(alice_friends.json()) == 1
    assert alice_friends.json()[0]["friend_id"] == bob["profile"]["id"]

    # Unfriend
    unfriend_resp = client.delete(f"/api/friends/{bob['profile']['id']}", headers=alice["headers"])
    assert unfriend_resp.status_code == 200

    # Now friends list is empty
    alice_friends_after = client.get("/api/friends", headers=alice["headers"])
    assert len(alice_friends_after.json()) == 0

def test_mutual_auto_acceptance(client: TestClient):
    charlie = create_user_helper(client, "charlie@cozy.com", name="Charlie")
    daisy = create_user_helper(client, "daisy@cozy.com", name="Daisy")

    # Charlie sends request to Daisy
    client.post("/api/friends/request", headers=charlie["headers"], json={
        "player_tag": daisy["profile"]["player_tag"]
    })

    # Daisy sends request back to Charlie -> auto-accepts!
    mutual_resp = client.post("/api/friends/request", headers=daisy["headers"], json={
        "player_tag": charlie["profile"]["player_tag"]
    })
    assert mutual_resp.status_code == 200
    assert "accepted" in mutual_resp.json()["message"].lower()

    # Both are friends immediately
    c_friends = client.get("/api/friends", headers=charlie["headers"])
    assert len(c_friends.json()) == 1
    d_friends = client.get("/api/friends", headers=daisy["headers"])
    assert len(d_friends.json()) == 1
