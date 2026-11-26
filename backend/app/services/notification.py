import json
import logging
from typing import List, Optional, Union

from app.config import settings

logger = logging.getLogger("notifications")

_firebase_app = None
_firebase_init_failed = False


def _firebase_credential_source() -> Union[dict, str, None]:
    """The service account as parsed JSON (FIREBASE_CREDENTIALS_JSON) or a file path, else None."""
    if settings.FIREBASE_CREDENTIALS_JSON.strip():
        return json.loads(settings.FIREBASE_CREDENTIALS_JSON)
    return settings.FIREBASE_CREDENTIALS_FILE or None


def _get_firebase_app():
    """Lazily initialises firebase-admin (None when no credentials are configured)."""
    global _firebase_app, _firebase_init_failed
    if _firebase_app is not None or _firebase_init_failed:
        return _firebase_app
    if not (settings.FIREBASE_CREDENTIALS_JSON.strip() or settings.FIREBASE_CREDENTIALS_FILE):
        return None
    try:
        import firebase_admin
        from firebase_admin import credentials

        _firebase_app = firebase_admin.initialize_app(
            credentials.Certificate(_firebase_credential_source()),
            name="cozy-doubling-push",
        )
    except Exception as exc:  # pragma: no cover - depends on deployment credentials
        _firebase_init_failed = True
        logger.error(f"Firebase initialisation failed; push notifications disabled: {exc}")
    return _firebase_app


def build_friend_focus_message(sender_name: Optional[str]) -> tuple[str, str]:
    title = "Friend Focusing!"
    body = f"{sender_name} just entered the focus room." if sender_name else "A friend just entered the focus room."
    return title, body


def send_friend_focus_notification(sender_name: Optional[str], friend_fcm_tokens: List[str]) -> int:
    """
    Dispatches push notifications to accepted friends upon entering the focus room ([ROOM-05]).
    Runs as a FastAPI background task. Returns the number of messages accepted by FCM.
    """
    if not friend_fcm_tokens:
        return 0

    title, body = build_friend_focus_message(sender_name)
    app = _get_firebase_app()
    if app is None:
        logger.info(f"FCM not configured; would push '{title}' to {len(friend_fcm_tokens)} device(s)")
        return 0

    from firebase_admin import messaging

    message = messaging.MulticastMessage(
        tokens=list(friend_fcm_tokens)[:500],
        notification=messaging.Notification(title=title, body=body),
        android=messaging.AndroidConfig(priority="high"),
    )
    try:
        result = messaging.send_each_for_multicast(message, app=app)
    except Exception as exc:  # pragma: no cover - network/credential failures
        logger.warning(f"FCM dispatch failed: {exc}")
        return 0
    if result.failure_count:
        logger.info(f"FCM: {result.success_count} sent, {result.failure_count} failed")
    return result.success_count
