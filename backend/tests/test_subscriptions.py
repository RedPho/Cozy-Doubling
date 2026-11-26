from starlette.testclient import TestClient
from app.config import settings
from tests.conftest import create_user_helper

WEBHOOK_HEADERS = {"Authorization": f"Bearer {settings.REVENUECAT_WEBHOOK_AUTH}"}

def test_revenuecat_webhook_and_auto_revert(client: TestClient):
    user = create_user_helper(client, "supporter_user@cozy.com")
    headers = user["headers"]
    user_id = user["profile"]["id"]

    assert user["profile"]["is_supporter"] is False

    # Cannot equip premium theme
    fail_equip = client.post("/api/inventory/equip", headers=headers, json={"theme_id": "theme_midnight_velvet"})
    assert fail_equip.status_code == 400

    # 1. RevenueCat Webhook: INITIAL_PURCHASE
    wh_resp = client.post("/api/subscriptions/webhook", headers=WEBHOOK_HEADERS, json={
        "event": {
            "type": "INITIAL_PURCHASE",
            "app_user_id": user_id
        }
    })
    assert wh_resp.status_code == 200

    prof_resp = client.get("/api/profile", headers=headers)
    assert prof_resp.json()["is_supporter"] is True

    # 2. Now user CAN equip premium theme
    equip_resp = client.post("/api/inventory/equip", headers=headers, json={"theme_id": "theme_midnight_velvet"})
    assert equip_resp.status_code == 200

    prof_theme = client.get("/api/profile", headers=headers)
    assert prof_theme.json()["equipped_theme_id"] == "theme_midnight_velvet"

    # 3. RevenueCat Webhook: EXPIRATION
    exp_resp = client.post("/api/subscriptions/webhook", headers=WEBHOOK_HEADERS, json={
        "event": {
            "type": "EXPIRATION",
            "app_user_id": user_id
        }
    })
    assert exp_resp.status_code == 200

    # Auto-reverts to system_default
    prof_after_exp = client.get("/api/profile", headers=headers)
    assert prof_after_exp.json()["is_supporter"] is False
    assert prof_after_exp.json()["equipped_theme_id"] == "system_default"
