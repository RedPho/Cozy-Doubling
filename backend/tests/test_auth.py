from starlette.testclient import TestClient
from tests.conftest import create_user_helper

def test_register_and_login_success(client: TestClient):
    reg_resp = client.post("/api/auth/register", json={
        "email": " TestUser@Cozy.com ",
        "password": "securepassword123",
        "display_name": "Test User"
    })
    assert reg_resp.status_code == 200
    token = reg_resp.json()["access_token"]
    assert token is not None

    me_resp = client.get("/api/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me_resp.status_code == 200
    profile = me_resp.json()
    assert profile["display_name"] == "Test User"
    assert profile["leaves"] == 0
    assert profile["total_minutes_focused"] == 0
    assert profile["is_supporter"] is False
    assert len(profile["player_tag"]) == 8
    for invalid_char in "01IO":
        assert invalid_char not in profile["player_tag"]

    login_resp = client.post("/api/auth/login", json={
        "email": "testuser@cozy.com",
        "password": "securepassword123"
    })
    assert login_resp.status_code == 200
    assert login_resp.json()["access_token"] is not None

def test_duplicate_registration_fails(client: TestClient):
    create_user_helper(client, "duplicate@cozy.com")
    resp = client.post("/api/auth/register", json={
        "email": "duplicate@cozy.com",
        "password": "password123"
    })
    assert resp.status_code == 400
    assert "already registered" in resp.json()["detail"].lower()

def test_invalid_login_credentials(client: TestClient):
    create_user_helper(client, "valid@cozy.com", "mypassword")

    resp1 = client.post("/api/auth/login", json={
        "email": "valid@cozy.com",
        "password": "wrongpassword"
    })
    assert resp1.status_code == 401
    assert resp1.json()["detail"] == "Invalid email or password"

    resp2 = client.post("/api/auth/login", json={
        "email": "nobody@cozy.com",
        "password": "password"
    })
    assert resp2.status_code == 401
    assert resp2.json()["detail"] == "Invalid email or password"

def test_google_sign_in_mock(client: TestClient):
    resp = client.post("/api/auth/google", json={
        "id_token": "mock:google:12345:Sam Cozy:sam@cozy.com"
    })
    assert resp.status_code == 200
    token = resp.json()["access_token"]

    me_resp = client.get("/api/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me_resp.status_code == 200
    profile = me_resp.json()
    assert profile["display_name"] == "Sam Cozy"
    assert len(profile["player_tag"]) == 8

def test_logout_and_account_deletion(client: TestClient):
    user = create_user_helper(client, "delete_me@cozy.com")
    headers = user["headers"]

    logout_resp = client.post("/api/auth/logout", headers=headers)
    assert logout_resp.status_code == 200

    del_resp = client.delete("/api/auth/account", headers=headers)
    assert del_resp.status_code == 200

    me_resp = client.get("/api/auth/me", headers=headers)
    assert me_resp.status_code == 401
