"""Verification of Google Sign-In ID tokens.

The Android client obtains a Google ID token through Credential Manager and sends it to
`POST /api/auth/google`. The token is a JWT signed by Google; it must be verified here
(signature, issuer, audience, expiry) before any account is created or returned. Trusting an
unverified token would let anyone sign in as any email address.
"""
from dataclasses import dataclass
from typing import Optional

from app.config import settings

_GOOGLE_ISSUERS = {"accounts.google.com", "https://accounts.google.com"}
_MOCK_PREFIX = "mock:google:"


class InvalidGoogleToken(Exception):
    pass


@dataclass(frozen=True)
class GoogleIdentity:
    subject: str          # Stable Google account ID ("sub" claim)
    email: str
    email_verified: bool
    name: Optional[str]


def _parse_mock_token(raw_token: str) -> GoogleIdentity:
    # Format used by local development and E2E tests: mock:google:<sub>:<name>:<email>
    parts = raw_token.split(":")
    subject = parts[2] if len(parts) > 2 and parts[2] else "google_test_user"
    name = parts[3] if len(parts) > 3 and parts[3] else None
    email = parts[4] if len(parts) > 4 and parts[4] else f"{subject}@gmail.com"
    return GoogleIdentity(subject=subject, email=email, email_verified=True, name=name)


def _verify_with_google(raw_token: str) -> dict:
    """Verifies signature + claims using Google's public keys. Split out for test patching."""
    try:
        from google.auth.transport import requests as google_requests
        from google.oauth2 import id_token as google_id_token
    except ImportError as exc:  # pragma: no cover - deployment misconfiguration
        raise InvalidGoogleToken("Google token verification library is not installed.") from exc

    try:
        # audience=None here; audience is checked below against every configured client ID.
        return google_id_token.verify_oauth2_token(raw_token, google_requests.Request(), audience=None)
    except ValueError as exc:
        raise InvalidGoogleToken("Invalid Google credential.") from exc


def verify_google_id_token(raw_token: str) -> GoogleIdentity:
    token = (raw_token or "").strip()
    if not token:
        raise InvalidGoogleToken("Missing Google credential.")

    if token.startswith(_MOCK_PREFIX):
        if not settings.allow_mock_google_tokens:
            raise InvalidGoogleToken("Invalid Google credential.")
        return _parse_mock_token(token)

    if not settings.GOOGLE_CLIENT_IDS:
        raise InvalidGoogleToken("Google Sign-In is not configured on this server.")

    claims = _verify_with_google(token)

    if claims.get("iss") not in _GOOGLE_ISSUERS:
        raise InvalidGoogleToken("Invalid Google credential.")
    if claims.get("aud") not in settings.GOOGLE_CLIENT_IDS:
        raise InvalidGoogleToken("Invalid Google credential.")
    subject = claims.get("sub")
    email = claims.get("email")
    if not subject or not email:
        raise InvalidGoogleToken("Invalid Google credential.")

    return GoogleIdentity(
        subject=str(subject),
        email=str(email),
        email_verified=bool(claims.get("email_verified")),
        name=claims.get("name"),
    )
