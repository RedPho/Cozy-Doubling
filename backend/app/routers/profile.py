from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from app.database import get_db
from app.models.user import User
from app.models.profile import Profile
from app.schemas.profile import ProfileResponse, ProfileUpdateRequest, FcmTokenUpdateRequest
from app.services.auth import get_current_user_and_profile

router = APIRouter(prefix="/api/profile", tags=["profile"])

@router.get("", response_model=ProfileResponse)
def get_profile(user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile)):
    _, profile = user_and_profile
    return ProfileResponse.from_orm_model(profile)

@router.patch("", response_model=ProfileResponse)
def update_profile(
    req: ProfileUpdateRequest,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    profile.display_name = req.display_name
    db.commit()
    db.refresh(profile)
    return ProfileResponse.from_orm_model(profile)

@router.post("/fcm-token")
def update_fcm_token(
    req: FcmTokenUpdateRequest,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    token = (req.fcm_token or "").strip() or None
    if token:
        # A device token belongs to exactly one signed-in account: detach it from any previous
        # account that used this device so its friend alerts stop reaching the new user.
        db.query(Profile).filter((Profile.fcm_token == token) & (Profile.id != profile.id)).update(
            {Profile.fcm_token: None}, synchronize_session=False
        )
    profile.fcm_token = token
    db.commit()
    return {"ok": True, "message": "FCM token updated"}

@router.delete("/fcm-token")
def clear_fcm_token(
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    """Called by the client on sign-out ([AUTH-06]); previously missing, so the call 405'd."""
    _, profile = user_and_profile
    profile.fcm_token = None
    db.commit()
    return {"ok": True, "message": "FCM token cleared"}
