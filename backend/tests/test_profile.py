from starlette.testclient import TestClient
from tests.conftest import create_user_helper

def test_profile_read_and_update_name(client: TestClient):
    user = create_user_helper(client, "profile_user@cozy.com", name="Original Name")
    headers = user["headers"]

    update_resp = client.patch("/api/profile", headers=headers, json={"display_name": "Updated Cozy"})
    assert update_resp.status_code == 200
    assert update_resp.json()["display_name"] == "Updated Cozy"

    blank_resp = client.patch("/api/profile", headers=headers, json={"display_name": "   "})
    assert blank_resp.status_code == 422

def test_profile_fcm_token_update(client: TestClient):
    user = create_user_helper(client, "fcm_user@cozy.com")
    headers = user["headers"]

    token_resp = client.post("/api/profile/fcm-token", headers=headers, json={"fcm_token": "fcm_sample_token_123"})
    assert token_resp.status_code == 200

    me_resp = client.get("/api/profile", headers=headers)
    assert me_resp.json()["fcm_token"] == "fcm_sample_token_123"
