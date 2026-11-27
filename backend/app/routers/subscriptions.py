import hmac
import logging
from typing import Optional
from fastapi import APIRouter, Depends, Header, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.models.user import User
from app.models.profile import Profile
from app.models.shop import Item
from app.services.auth import get_current_user_and_profile
from app.services.revenuecat import RevenueCatUnavailable, fetch_supporter_entitlement

logger = logging.getLogger("subscriptions")

router = APIRouter(prefix="/api/subscriptions", tags=["subscriptions"])

class SyncSubscriptionRequest(BaseModel):
    # Only honoured outside production (fake billing in local dev / E2E). In production the
    # supporter status is always read from RevenueCat, never from the client.
    is_supporter: Optional[bool] = None

class WebhookPayload(BaseModel):
    event: dict


def _apply_supporter_status(db: Session, profile: Profile, is_supporter: bool) -> None:
    profile.is_supporter = is_supporter
    # Auto-revert if the currently equipped theme is premium and the pass is no longer active
    if not is_supporter and profile.equipped_theme_id and profile.equipped_theme_id != "system_default":
        theme = db.query(Item).filter(Item.id == profile.equipped_theme_id).first()
        if theme and theme.is_premium:
            profile.equipped_theme_id = "system_default"


def _webhook_authorized(authorization: Optional[str]) -> bool:
    expected = settings.REVENUECAT_WEBHOOK_AUTH
    if not authorization or not expected:
        return False
    candidates = {authorization.strip()}
    if authorization.lower().startswith("bearer "):
        candidates.add(authorization[7:].strip())
    return any(hmac.compare_digest(c.encode(), expected.encode()) for c in candidates)


@router.post("/webhook")
def revenuecat_webhook(
    payload: WebhookPayload,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    if not _webhook_authorized(authorization):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid webhook authorization")

    event = payload.event
    event_type = event.get("type", "")
    app_user_id = event.get("app_user_id", "")

    if not app_user_id:
        return {"ok": True, "ignored": "No app_user_id"}

    profile = db.query(Profile).filter(Profile.id == app_user_id).first()
    if not profile:
        return {"ok": True, "ignored": "User profile not found"}

    positive_events = {"INITIAL_PURCHASE", "RENEWAL", "RESTORE", "TRANSFER", "UNCANCELLATION", "NON_RENEWING_PURCHASE"}
    negative_events = {"EXPIRATION", "REVOCATION"}

    if event_type in positive_events:
        _apply_supporter_status(db, profile, True)
    elif event_type in negative_events:
        _apply_supporter_status(db, profile, False)

    db.commit()
    return {"ok": True, "status": "processed"}

@router.post("/sync")
def sync_subscription(
    req: SyncSubscriptionRequest,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    """Re-evaluates supporter status ("Restore Purchases" / post-purchase refresh)."""
    _, profile = user_and_profile

    if settings.REVENUECAT_SECRET_KEY:
        try:
            is_supporter = fetch_supporter_entitlement(profile.id)
        except RevenueCatUnavailable as exc:
            logger.warning(f"RevenueCat entitlement check failed for {profile.id}: {exc}")
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Could not verify purchases right now. Please try again shortly."
            )
        _apply_supporter_status(db, profile, is_supporter)
    elif settings.allow_client_supporter_sync and req.is_supporter is not None:
        _apply_supporter_status(db, profile, req.is_supporter)
    else:
        # Production without RevenueCat REST access: only the authenticated webhook may change
        # supporter status. Still enforce the premium-theme revert for lapsed supporters.
        _apply_supporter_status(db, profile, profile.is_supporter)

    db.commit()
    db.refresh(profile)
    return {"ok": True, "is_supporter": profile.is_supporter}
