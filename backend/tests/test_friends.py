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

    # Now both friends lists are empty
    alice_friends_after = client.get("/api/friends", headers=alice["headers"])
    assert len(alice_friends_after.json()) == 0
    bob_friends_after = client.get("/api/friends", headers=bob["headers"])
    assert len(bob_friends_after.json()) == 0

    # Either user can send a new friend request later without being blocked
    readd_resp = client.post("/api/friends/request", headers=bob["headers"], json={
        "player_tag": alice["profile"]["player_tag"]
    })
    assert readd_resp.status_code == 200
    alice_pending_again = client.get("/api/friends/requests", headers=alice["headers"])
    assert len(alice_pending_again.json()) == 1
    assert alice_pending_again.json()[0]["id"] == bob["profile"]["id"]

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


def test_cannot_add_friend_when_blocked_either_direction(client: TestClient):
    alice = create_user_helper(client, "block_alice@cozy.com", name="Alice")
    bob = create_user_helper(client, "block_bob@cozy.com", name="Bob")
    charlie = create_user_helper(client, "block_charlie@cozy.com", name="Charlie")

    # 1. Alice blocks Bob -> Alice cannot send a friend request to Bob (403 with clear UX hint)
    assert client.post("/api/safety/block", headers=alice["headers"], json={
        "target_id": bob["profile"]["id"]
    }).status_code == 200

    a_to_b = client.post("/api/friends/request", headers=alice["headers"], json={
        "player_tag": bob["profile"]["player_tag"]
    })
    assert a_to_b.status_code == 403
    assert "blocked" in a_to_b.json()["detail"].lower()
    assert "settings" in a_to_b.json()["detail"].lower()

    # 2. Bob also cannot send a friend request to Alice (403 without leaking blocker identity)
    b_to_a = client.post("/api/friends/request", headers=bob["headers"], json={
        "player_tag": alice["profile"]["player_tag"]
    })
    assert b_to_a.status_code == 403
    assert "unable to send friend request" in b_to_a.json()["detail"].lower()

    alice_reqs = client.get("/api/friends/requests", headers=alice["headers"]).json()
    assert alice_reqs == []

    # 3. Charlie sends pending request to Alice, then Alice blocks Charlie -> cannot accept
    assert client.post("/api/friends/request", headers=charlie["headers"], json={
        "player_tag": alice["profile"]["player_tag"]
    }).status_code == 200
    assert client.post("/api/safety/block", headers=alice["headers"], json={
        "target_id": charlie["profile"]["id"]
    }).status_code == 200

    accept_blocked = client.post(
        f"/api/friends/{charlie['profile']['id']}/accept",
        headers=alice["headers"]
    )
    assert accept_blocked.status_code == 403
    assert "blocked" in accept_blocked.json()["detail"].lower()

    # 4. Alice unblocks Bob -> now Alice can send a friend request to Bob cleanly
    assert client.post("/api/safety/unblock", headers=alice["headers"], json={
        "target_id": bob["profile"]["id"]
    }).status_code == 200

    a_to_b_after = client.post("/api/friends/request", headers=alice["headers"], json={
        "player_tag": bob["profile"]["player_tag"]
    })
    assert a_to_b_after.status_code == 200
    bob_reqs = client.get("/api/friends/requests", headers=bob["headers"]).json()
    assert len(bob_reqs) == 1
    assert bob_reqs[0]["id"] == alice["profile"]["id"]
