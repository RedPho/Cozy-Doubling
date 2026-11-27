from app.config import settings
from app.services import notification


def test_json_credentials_win_over_file(monkeypatch):
    monkeypatch.setattr(settings, "FIREBASE_CREDENTIALS_JSON", '{"type": "x", "project_id": "p"}')
    monkeypatch.setattr(settings, "FIREBASE_CREDENTIALS_FILE", "/run/firebase.json")
    assert notification._firebase_credential_source() == {"type": "x", "project_id": "p"}


def test_file_credentials_used_without_json(monkeypatch):
    monkeypatch.setattr(settings, "FIREBASE_CREDENTIALS_JSON", "")
    monkeypatch.setattr(settings, "FIREBASE_CREDENTIALS_FILE", "/run/firebase.json")
    assert notification._firebase_credential_source() == "/run/firebase.json"


def test_no_credentials(monkeypatch):
    monkeypatch.setattr(settings, "FIREBASE_CREDENTIALS_JSON", "")
    monkeypatch.setattr(settings, "FIREBASE_CREDENTIALS_FILE", "")
    assert notification._firebase_credential_source() is None
    assert notification._get_firebase_app() is None
