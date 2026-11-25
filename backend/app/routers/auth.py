import re
import uuid
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.config import settings

from app.database import get_db
from app.models.user import User
from app.models.profile import Profile
from app.models.shop import UserInventory
from app.models.friendship import Friendship
from app.models.session import FocusSession
from app.models.safety import UserBlock, UserReport
from app.schemas.auth import (
    UserRegisterRequest,
    UserLoginRequest,
    GoogleAuthRequest,
    TokenResponse,
)
from app.schemas.profile import ProfileResponse
from app.services.auth import (
    hash_password,
    verify_password,
    create_access_token,
    get_current_user_and_profile,
)
from app.services.google_identity import InvalidGoogleToken, verify_google_id_token
from app.services.login_throttle import login_throttle
from app.services.tag_generator import generate_player_tag

EMAIL_PATTERN = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")

router = APIRouter(prefix="/api/auth", tags=["auth"])

def _create_user_with_profile(
    db: Session,
    email: str,
    hashed_password: Optional[str],
    display_name: Optional[str],
    google_sub: Optional[str] = None,
) -> User:
    user_id = str(uuid.uuid4())
    user = User(id=user_id, email=email, hashed_password=hashed_password, google_sub=google_sub)
    db.add(user)
    db.flush()

    clean_name = (display_name or "").strip()[:30]
    profile = Profile(
        id=user_id,
        display_name=clean_name or "Cozy Doubler",
        player_tag=generate_player_tag(db),
        equipped_theme_id="system_default",
        leaves=0,
        total_minutes_focused=0,
        is_supporter=False
    )
    db.add(profile)
    db.add(UserInventory(user_id=user_id, item_id="system_default"))
    return user


@router.post("/register", response_model=TokenResponse)
def register(req: UserRegisterRequest, db: Session = Depends(get_db)):
    clean_email = req.email.strip().lower()
    if not EMAIL_PATTERN.match(clean_email):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Please enter a valid email address.")
    if len(req.password) < settings.MIN_PASSWORD_LENGTH:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Password must be at least {settings.MIN_PASSWORD_LENGTH} characters."
        )

    existing = db.query(User).filter(User.email == clean_email).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Email already registered"
        )

    user = _create_user_with_profile(
        db,
        email=clean_email,
        hashed_password=hash_password(req.password),
        display_name=req.display_name,
    )
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Email already registered")

    token = create_access_token({"sub": user.id, "email": clean_email})
    return TokenResponse(access_token=token)

@router.post("/login", response_model=TokenResponse)
def login(req: UserLoginRequest, request: Request, db: Session = Depends(get_db)):
    clean_email = req.email.strip().lower()
    client_ip = request.client.host if request.client else "unknown"
    throttle_key = f"{clean_email}|{client_ip}"

    retry_after = login_throttle.retry_after_seconds(throttle_key)
    if retry_after > 0:
        minutes = max(1, (retry_after + 59) // 60)
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"Too many failed sign-in attempts. Please try again in {minutes} minute(s).",
            headers={"Retry-After": str(retry_after)},
        )

    user = db.query(User).filter(User.email == clean_email).first()

    # Generic error message to prevent user enumeration
    generic_error = "Invalid email or password"
    if not user or not user.hashed_password or not verify_password(req.password, user.hashed_password):
        login_throttle.record_failure(throttle_key)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=generic_error
        )

    login_throttle.reset(throttle_key)
    token = create_access_token({"sub": user.id, "email": user.email})
    return TokenResponse(access_token=token)

@router.post("/google", response_model=TokenResponse)
def google_auth(req: GoogleAuthRequest, db: Session = Depends(get_db)):
    try:
        identity = verify_google_id_token(req.id_token)
    except InvalidGoogleToken as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=str(exc))

    clean_email = identity.email.strip().lower()

    # 1. Match on the stable Google account ID.
    user = db.query(User).filter(User.google_sub == identity.subject).first()

    # 2. Link an existing account with the same email only when Google has verified that email,
    #    otherwise an unverified Google account could take over an email/password account.
    if not user:
        by_email = db.query(User).filter(User.email == clean_email).first()
        if by_email:
            if not identity.email_verified or (by_email.google_sub and by_email.google_sub != identity.subject):
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="An account with this email already exists. Please sign in with your email and password."
                )
            by_email.google_sub = identity.subject
            user = by_email
            db.commit()

    # 3. First sign-in: provision a new account and profile.
    if not user:
        user = _create_user_with_profile(
            db,
            email=clean_email,
            hashed_password=None,
            display_name=identity.name,
            google_sub=identity.subject,
        )
        try:
            db.commit()
        except IntegrityError:
            # Concurrent first sign-in for the same Google account: resolve to the row that won.
            db.rollback()
            user = db.query(User).filter(User.google_sub == identity.subject).first()
            if not user:
                raise

    token = create_access_token({"sub": user.id, "email": user.email})
    return TokenResponse(access_token=token)

@router.get("/me", response_model=ProfileResponse)
def get_me(user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile)):
    _, profile = user_and_profile
    return ProfileResponse.from_orm_model(profile)

@router.post("/logout")
def logout(
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    profile.fcm_token = None
    db.commit()
    return {"ok": True, "message": "Logged out successfully"}

@router.delete("/account")
def delete_account(
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    user, profile = user_and_profile
    my_id = user.id

    # 1. User inventory
    db.query(UserInventory).filter(UserInventory.user_id == my_id).delete()
    # 2. Friendships
    db.query(Friendship).filter((Friendship.user_id_1 == my_id) | (Friendship.user_id_2 == my_id)).delete()
    # 3. Focus sessions
    db.query(FocusSession).filter(FocusSession.user_id == my_id).delete()
    # 4. User blocks
    db.query(UserBlock).filter((UserBlock.blocker_id == my_id) | (UserBlock.blocked_id == my_id)).delete()
    # 5. User reports: reports against the user are removed; reports the user filed are kept
    #    anonymously for moderation (reporter unlinked). Done explicitly because SQLite does not
    #    enforce the ON DELETE SET NULL foreign key.
    db.query(UserReport).filter(UserReport.reported_id == my_id).delete()
    db.query(UserReport).filter(UserReport.reporter_id == my_id).update({UserReport.reporter_id: None})
    # 6. Profile
    db.query(Profile).filter(Profile.id == my_id).delete()
    # 7. User
    db.query(User).filter(User.id == my_id).delete()

    db.commit()
    return {"ok": True, "message": "Account deleted permanently"}
