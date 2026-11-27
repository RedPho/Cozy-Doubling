"""Server-side entitlement lookup against the RevenueCat REST API (v1 subscribers endpoint)."""
import datetime
from urllib.parse import quote

import httpx

from app.config import settings

_SUBSCRIBERS_URL = "https://api.revenuecat.com/v1/subscribers/{app_user_id}"


class RevenueCatUnavailable(Exception):
    pass


def _parse_expiry(value):
    if value is None:
        return None  # Lifetime / non-expiring entitlement
    try:
        return datetime.datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return datetime.datetime.min.replace(tzinfo=datetime.timezone.utc)


def is_entitlement_active(subscriber: dict, entitlement_id: str, now: datetime.datetime | None = None) -> bool:
    now = now or datetime.datetime.now(datetime.timezone.utc)
    entitlement = (subscriber.get("entitlements") or {}).get(entitlement_id)
    if not entitlement:
        return False
    expires = _parse_expiry(entitlement.get("expires_date"))
    return expires is None or expires > now


def fetch_supporter_entitlement(app_user_id: str) -> bool:
    """Returns True when the user currently holds the supporter entitlement in RevenueCat."""
    try:
        response = httpx.get(
            _SUBSCRIBERS_URL.format(app_user_id=quote(app_user_id, safe="")),
            headers={"Authorization": f"Bearer {settings.REVENUECAT_SECRET_KEY}"},
            timeout=10.0,
        )
    except httpx.HTTPError as exc:
        raise RevenueCatUnavailable(str(exc)) from exc
    if response.status_code != 200:
        raise RevenueCatUnavailable(f"HTTP {response.status_code}")
    subscriber = (response.json() or {}).get("subscriber") or {}
    return is_entitlement_active(subscriber, settings.SUPPORTER_ENTITLEMENT_ID)
