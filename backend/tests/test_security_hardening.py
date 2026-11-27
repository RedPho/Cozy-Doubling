"""Regression tests for the backend security hardening (auth, billing, economy, presence)."""
import json

import pytest
from starlette.testclient import TestClient

from app.config import settings
from app.database import get_db
from app.main import app
from app.models.safety import UserReport
from app.services import google_identity, revenuecat
from app.websockets.room_manager import focus_room_manager
from tests.conftest import create_user_helper, seeded_token

WEBHOOK_HEADERS = {"Authorization": f"Bearer {settings.REVENUECAT_WEBHOOK_AUTH}"}
WEB_CLIENT_ID = "1234-web.apps.googleusercontent.com"


@pytest.fixture
def production(monkeypatch):
    monkeypatch.setattr(settings, "ENVIRONMENT", "production")
    monkeypatch.setattr(settings, "GOOGLE_CLIENT_IDS", [WEB_CLIENT_ID])


def _fake_google(monkeypatch, **claims):
    base = {
        "iss": "https://accounts.google.com",
        "aud": WEB_CLIENT_ID,
        "sub": "google-sub-1",
        "email": "sam@gmail.com",
        "email_verified": True,
        "name": "Sam Cozy",
    }
    base.update(claims)
    monkeypatch.setattr(google_identity, "_verify_with_google", lambda token: base)


# ── Google Sign-In ───────────────────────────────────────────────────────────

def test_mock_google_token_rejected_in_production(client: TestClient, production):
    resp = client.post("/api/auth/google", json={"id_token": "mock:google:x:Evil:victim@gmail.com"})
    assert resp.status_code == 401


def test_verified_google_token_provisions_and_restores_same_account(client: TestClient, production, monkeypatch):
    _fake_google(monkeypatch)
    first = client.post("/api/auth/google", json={"id_token": "real.jwt.token"})
    assert first.status_code == 200
    me1 = client.get("/api/auth/me", headers={"Authorization": f"Bearer {first.json()['access_token']}"}).json()
    assert me1["display_name"] == "Sam Cozy"

    second = client.post("/api/auth/google", json={"id_token": "real.jwt.token"})
    me2 = client.get("/api/auth/me", headers={"Authorization": f"Bearer {second.json()['access_token']}"}).json()
    assert me2["id"] == me1["id"]


def test_google_token_for_other_audience_rejected(client: TestClient, production, monkeypatch):
    _fake_google(monkeypatch, aud="someone-elses-app.apps.googleusercontent.com")
    resp = client.post("/api/auth/google", json={"id_token": "real.jwt.token"})
    assert resp.status_code == 401


def test_unverified_google_email_cannot_take_over_password_account(client: TestClient, production, monkeypatch):
    victim = create_user_helper(client, "victim@gmail.com", password="correct-horse-1")
    _fake_google(monkeypatch, email="victim@gmail.com", email_verified=False, sub="attacker-sub")
    resp = client.post("/api/auth/google", json={"id_token": "real.jwt.token"})
    assert resp.status_code == 409

    # Verified Google email for the same address links to the existing account instead.
    _fake_google(monkeypatch, email="victim@gmail.com", email_verified=True, sub="owner-sub")
    linked = client.post("/api/auth/google", json={"id_token": "real.jwt.token"})
    assert linked.status_code == 200
    me = client.get("/api/auth/me", headers={"Authorization": f"Bearer {linked.json()['access_token']}"}).json()
    assert me["id"] == victim["profile"]["id"]


# ── Email registration & login ───────────────────────────────────────────────

def test_register_enforces_password_length_and_email_format(client: TestClient):
    short = client.post("/api/auth/register", json={"email": "a@b.co", "password": "short"})
    assert short.status_code == 400
    assert "at least 8" in short.json()["detail"]

    bad_email = client.post("/api/auth/register", json={"email": "not-an-email", "password": "long-enough-pw"})
    assert bad_email.status_code == 400


def test_repeated_failed_logins_are_rate_limited(client: TestClient):
    create_user_helper(client, "throttle@cozy.com", password="right-password")
    for _ in range(5):
        resp = client.post("/api/auth/login", json={"email": "throttle@cozy.com", "password": "wrong-password"})
        assert resp.status_code == 401
    locked = client.post("/api/auth/login", json={"email": "throttle@cozy.com", "password": "right-password"})
    assert locked.status_code == 429
    assert "try again" in locked.json()["detail"].lower()


# ── Subscriptions ────────────────────────────────────────────────────────────

def test_webhook_requires_shared_secret(client: TestClient):
    user = create_user_helper(client, "rc@cozy.com")
    body = {"event": {"type": "INITIAL_PURCHASE", "app_user_id": user["profile"]["id"]}}

    assert client.post("/api/subscriptions/webhook", json=body).status_code == 401
    assert client.post("/api/subscriptions/webhook", json=body, headers={"Authorization": "Bearer wrong"}).status_code == 401
    assert client.get("/api/profile", headers=user["headers"]).json()["is_supporter"] is False

    assert client.post("/api/subscriptions/webhook", json=body, headers=WEBHOOK_HEADERS).status_code == 200
    assert client.get("/api/profile", headers=user["headers"]).json()["is_supporter"] is True


def test_client_cannot_grant_itself_supporter_in_production(client: TestClient, production):
    user = create_user_helper(client, "freeloader@cozy.com")
    resp = client.post("/api/subscriptions/sync", headers=user["headers"], json={"is_supporter": True})
    assert resp.status_code == 200
    assert resp.json()["is_supporter"] is False


def test_sync_uses_revenuecat_entitlement_when_configured(client: TestClient, production, monkeypatch):
    user = create_user_helper(client, "restorer@cozy.com")
    monkeypatch.setattr(settings, "REVENUECAT_SECRET_KEY", "sk_test")
    calls = []

    def fake_fetch(app_user_id):
        calls.append(app_user_id)
        return True

    monkeypatch.setattr("app.routers.subscriptions.fetch_supporter_entitlement", fake_fetch)
    resp = client.post("/api/subscriptions/sync", headers=user["headers"], json={})
    assert resp.json()["is_supporter"] is True
    assert calls == [user["profile"]["id"]]


def test_revenuecat_entitlement_expiry_parsing():
    active = {"entitlements": {settings.SUPPORTER_ENTITLEMENT_ID: {"expires_date": "2999-01-01T00:00:00Z"}}}
    lifetime = {"entitlements": {settings.SUPPORTER_ENTITLEMENT_ID: {"expires_date": None}}}
    expired = {"entitlements": {settings.SUPPORTER_ENTITLEMENT_ID: {"expires_date": "2000-01-01T00:00:00Z"}}}
    assert revenuecat.is_entitlement_active(active, settings.SUPPORTER_ENTITLEMENT_ID)
    assert revenuecat.is_entitlement_active(lifetime, settings.SUPPORTER_ENTITLEMENT_ID)
    assert not revenuecat.is_entitlement_active(expired, settings.SUPPORTER_ENTITLEMENT_ID)
    assert not revenuecat.is_entitlement_active({}, settings.SUPPORTER_ENTITLEMENT_ID)


# ── Leaf economy ─────────────────────────────────────────────────────────────

def test_finishing_unknown_session_is_rejected(client: TestClient):
    user = create_user_helper(client, "farmer@cozy.com")
    resp = client.post("/api/sessions/never-started/finish", headers=user["headers"], json={"tasks_completed": 5})
    assert resp.status_code == 404
    assert client.get("/api/profile", headers=user["headers"]).json()["leaves"] == 0


def test_task_rewards_are_capped_per_session(client: TestClient):
    user = create_user_helper(client, "greedy@cozy.com")
    sid = client.post("/api/sessions/start", headers=user["headers"]).json()["session_id"]
    finished = client.post(f"/api/sessions/{sid}/finish", headers=user["headers"], json={"tasks_completed": 1000})
    assert finished.status_code == 200
    assert finished.json()["earned_leaves"] == settings.MAX_REWARDED_TASKS_PER_SESSION * 3

    sid2 = client.post("/api/sessions/start", headers=user["headers"]).json()["session_id"]
    too_many = client.post(f"/api/sessions/{sid2}/finish", headers=user["headers"], json={"tasks_completed": 10**9})
    assert too_many.status_code == 422


# ── Focus room presence ──────────────────────────────────────────────────────

def test_presence_key_and_name_cannot_be_spoofed(client: TestClient):
    focus_room_manager.clear()
    victim_token = seeded_token("victim_user", "Victim")
    attacker_token = seeded_token("attacker_user", "Attacker")

    with client.websocket_connect(f"/ws/room?token={victim_token}&presence_key=victim_user:s1") as victim_ws:
        assert json.loads(victim_ws.receive_text())["type"] == "presence_state"

        spoof_url = f"/ws/room?token={attacker_token}&presence_key=victim_user:s1&name=Victim"
        with client.websocket_connect(spoof_url) as attacker_ws:
            state = json.loads(attacker_ws.receive_text())
            assert [p["id"] for p in state["participants"]] == ["victim_user:s1"]

            joined = json.loads(victim_ws.receive_text())
            assert joined["type"] == "presence_update"
            assert joined["presence"]["id"] == "attacker_user:s1"
            assert joined["presence"]["name"] == "Attacker"

            # The victim's socket was not replaced by the attacker's connection.
            assert "victim_user:s1" in focus_room_manager.active_participants
            assert focus_room_manager.active_participants["victim_user:s1"].user_id == "victim_user"

            attacker_ws.send_text(json.dumps({"type": "presence_update", "presence": {"name": "Victim", "active_task": "x"}}))
            update = json.loads(victim_ws.receive_text())
            assert update["presence"]["name"] == "Attacker"
    focus_room_manager.clear()


def test_room_rejects_token_of_deleted_user(client: TestClient):
    from app.services.auth import create_access_token

    ghost = create_access_token({"sub": "deleted-user", "email": "ghost@cozy.com"})
    from starlette.websockets import WebSocketDisconnect

    with pytest.raises(WebSocketDisconnect):
        with client.websocket_connect(f"/ws/room?token={ghost}&presence_key=deleted-user:s") as ws:
            ws.receive_text()


# ── Push tokens & account lifecycle ──────────────────────────────────────────

def test_fcm_token_can_be_cleared_and_moves_between_accounts(client: TestClient):
    a = create_user_helper(client, "device_a@cozy.com")
    b = create_user_helper(client, "device_b@cozy.com")
    client.post("/api/profile/fcm-token", headers=a["headers"], json={"fcm_token": "device-token-1"})
    client.post("/api/profile/fcm-token", headers=b["headers"], json={"fcm_token": "device-token-1"})

    assert client.get("/api/profile", headers=a["headers"]).json()["fcm_token"] is None
    assert client.get("/api/profile", headers=b["headers"]).json()["fcm_token"] == "device-token-1"

    assert client.delete("/api/profile/fcm-token", headers=b["headers"]).status_code == 200
    assert client.get("/api/profile", headers=b["headers"]).json()["fcm_token"] is None


def test_account_deletion_unlinks_reports_filed_by_user(client: TestClient):
    reporter = create_user_helper(client, "reporter@cozy.com")
    target = create_user_helper(client, "target@cozy.com")
    resp = client.post("/api/safety/report", headers=reporter["headers"], json={
        "target_id": target["profile"]["id"], "reason": "Offensive name"
    })
    assert resp.status_code == 200

    assert client.delete("/api/auth/account", headers=reporter["headers"]).status_code == 200

    gen = app.dependency_overrides[get_db]()
    db = next(gen)
    try:
        reports = db.query(UserReport).all()
        assert len(reports) == 1
        assert reports[0].reporter_id is None
        assert reports[0].reason == "Offensive name"
    finally:
        gen.close()


# ── Production configuration guard ───────────────────────────────────────────

def test_production_refuses_development_secrets(monkeypatch):
    monkeypatch.setattr(settings, "ENVIRONMENT", "production")
    monkeypatch.setattr(settings, "GOOGLE_CLIENT_IDS", [])
    problems = " ".join(settings.validate())
    assert "SECRET_KEY" in problems
    assert "REVENUECAT_WEBHOOK_AUTH" in problems
    assert "GOOGLE_CLIENT_IDS" in problems
    assert "DATABASE_URL" in problems

    monkeypatch.setattr(settings, "ENVIRONMENT", "development")
    assert settings.validate() == []
