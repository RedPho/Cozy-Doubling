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

router = APIRouter(prefix="/api/subscriptions", tags=["subscriptions"])

class SyncSubscriptionRequest(BaseModel):
    is_supporter: Optional[bool] = None

class WebhookPayload(BaseModel):
    event: dict

@router.post("/webhook")
def revenuecat_webhook(
    payload: WebhookPayload,
    authorization: Optional[str] = Header(None),
    db: Session = Depends(get_db)
):
    event = payload.event
    event_type = event.get("type", "")
    app_user_id = event.get("app_user_id", "")

    if not app_user_id:
        return {"ok": True, "ignored": "No app_user_id"}

    profile = db.query(Profile).filter(Profile.id == app_user_id).first()
    if not profile:
        return {"ok": True, "ignored": "User profile not found"}

    positive_events = {"INITIAL_PURCHASE", "RENEWAL", "RESTORE", "TRANSFER"}
    negative_events = {"EXPIRATION", "REVOCATION"}

    if event_type in positive_events:
        profile.is_supporter = True
    elif event_type in negative_events:
        profile.is_supporter = False

        # Auto-revert if currently equipped theme is premium
        if profile.equipped_theme_id and profile.equipped_theme_id != "system_default":
            theme = db.query(Item).filter(Item.id == profile.equipped_theme_id).first()
            if theme and theme.is_premium:
                profile.equipped_theme_id = "system_default"

    db.commit()
    return {"ok": True, "status": "processed"}

@router.post("/sync")
def sync_subscription(
    req: SyncSubscriptionRequest,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    if req.is_supporter is not None:
        profile.is_supporter = req.is_supporter

    if not profile.is_supporter and profile.equipped_theme_id and profile.equipped_theme_id != "system_default":
        theme = db.query(Item).filter(Item.id == profile.equipped_theme_id).first()
        if theme and theme.is_premium:
            profile.equipped_theme_id = "system_default"

    db.commit()
    db.refresh(profile)
    return {"ok": True, "is_supporter": profile.is_supporter}
