from starlette.testclient import TestClient
from tests.conftest import create_user_helper

def test_shop_items_listing_and_leaf_purchase(client: TestClient):
    user = create_user_helper(client, "shopper@cozy.com")
    headers = user["headers"]

    # 1. List shop items
    items_resp = client.get("/api/shop/items", headers=headers)
    assert items_resp.status_code == 200
    items = items_resp.json()
    assert len(items) >= 3

    peach_theme = next(i for i in items if i["id"] == "theme_peach_blossom")
    assert peach_theme["leaf_price"] == 100
    assert peach_theme["is_owned"] is False
    assert peach_theme["palette"] is not None

    # 2. Try to buy without enough leaves -> fails
    fail_buy = client.post("/api/shop/buy", headers=headers, json={"item_id": "theme_peach_blossom"})
    assert fail_buy.status_code == 400
    assert "not enough leaves" in fail_buy.json()["detail"].lower()

    # 3. Earn enough leaves via two focus sessions (task rewards are capped at 20 per session:
    #    2 x 20 tasks x 3 = 120 leaves)
    for _ in range(2):
        start_resp = client.post("/api/sessions/start", headers=headers)
        session_id = start_resp.json()["session_id"]
        client.post(f"/api/sessions/{session_id}/finish", headers=headers, json={
            "tasks_completed": 20,
            "last_task_text": "Massive study session"
        })

    # Verify leaves
    profile_resp = client.get("/api/profile", headers=headers)
    assert profile_resp.json()["leaves"] >= 100

    # 4. Now buy the theme
    buy_resp = client.post("/api/shop/buy", headers=headers, json={"item_id": "theme_peach_blossom"})
    assert buy_resp.status_code == 200
    assert buy_resp.json()["ok"] is True

    # 5. Cannot repurchase already owned item
    dup_buy = client.post("/api/shop/buy", headers=headers, json={"item_id": "theme_peach_blossom"})
    assert dup_buy.status_code == 400
    assert "already own" in dup_buy.json()["detail"].lower()

    # 6. Cannot buy premium theme with leaves
    prem_buy = client.post("/api/shop/buy", headers=headers, json={"item_id": "theme_midnight_velvet"})
    assert prem_buy.status_code == 400
    assert "supporter pass" in prem_buy.json()["detail"].lower()

    # 7. Equip purchased theme
    equip_resp = client.post("/api/inventory/equip", headers=headers, json={"theme_id": "theme_peach_blossom"})
    assert equip_resp.status_code == 200

    prof_after = client.get("/api/profile", headers=headers)
    assert prof_after.json()["equipped_theme_id"] == "theme_peach_blossom"

    # 8. Unequip theme -> reverts to system_default
    unequip_resp = client.post("/api/inventory/unequip", headers=headers)
    assert unequip_resp.status_code == 200
    prof_default = client.get("/api/profile", headers=headers)
    assert prof_default.json()["equipped_theme_id"] == "system_default"
