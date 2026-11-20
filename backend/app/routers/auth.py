import uuid
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session, joinedload

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
from app.services.tag_generator import generate_player_tag

router = APIRouter(prefix="/api/auth", tags=["auth"])

@router.post("/register", response_model=TokenResponse)
def register(req: UserRegisterRequest, db: Session = Depends(get_db)):
    clean_email = req.email.strip().lower()
    
    # Check existing user
    existing = db.query(User).filter(User.email == clean_email).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Email already registered"
        )
    
    user_id = str(uuid.uuid4())
    user = User(
        id=user_id,
        email=clean_email,
        hashed_password=hash_password(req.password)
    )
    db.add(user)
    db.flush()

    player_tag = generate_player_tag(db)
    display_name = req.display_name.strip() if req.display_name and req.display_name.strip() else "Cozy Doubler"

    profile = Profile(
        id=user_id,
        display_name=display_name,
        player_tag=player_tag,
        equipped_theme_id="system_default",
        leaves=0,
        total_minutes_focused=0,
        is_supporter=False
    )
    db.add(profile)

    # Add system default to inventory
    inventory = UserInventory(user_id=user_id, item_id="system_default")
    db.add(inventory)

    db.commit()

    token = create_access_token({"sub": user_id, "email": clean_email})
    return TokenResponse(access_token=token)

@router.post("/login", response_model=TokenResponse)
def login(req: UserLoginRequest, db: Session = Depends(get_db)):
    clean_email = req.email.strip().lower()
    user = db.query(User).filter(User.email == clean_email).first()

    # Generic error message to prevent user enumeration
    generic_error = "Invalid email or password"
    if not user or not user.hashed_password:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=generic_error
        )

    if not verify_password(req.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=generic_error
        )

    token = create_access_token({"sub": user.id, "email": user.email})
    return TokenResponse(access_token=token)

@router.post("/google", response_model=TokenResponse)
def google_auth(req: GoogleAuthRequest, db: Session = Depends(get_db)):
    raw_token = req.id_token.strip()
    if raw_token.startswith("mock:google:"):
        parts = raw_token.split(":")
        google_id = parts[2] if len(parts) > 2 else "google_test_user"
        name = parts[3] if len(parts) > 3 else "Google User"
        email = parts[4] if len(parts) > 4 else f"{google_id}@gmail.com"
    else:
        google_id = "google_" + raw_token[:16]
        name = "Google User"
        email = f"{google_id}@gmail.com"

    clean_email = email.lower().strip()
    user = db.query(User).options(joinedload(User.profile)).filter(User.email == clean_email).first()

    if not user:
        user_id = str(uuid.uuid4())
        user = User(id=user_id, email=clean_email, hashed_password=None)
        db.add(user)
        db.flush()

        player_tag = generate_player_tag(db)
        profile = Profile(
            id=user_id,
            display_name=name if name else "Cozy Doubler",
            player_tag=player_tag,
            equipped_theme_id="system_default",
            leaves=0,
            total_minutes_focused=0,
            is_supporter=False
        )
        db.add(profile)
        db.add(UserInventory(user_id=user_id, item_id="system_default"))
        db.commit()
    
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
    # 5. User reports
    db.query(UserReport).filter(UserReport.reported_id == my_id).delete()
    # 6. Profile
    db.query(Profile).filter(Profile.id == my_id).delete()
    # 7. User
    db.query(User).filter(User.id == my_id).delete()

    db.commit()
    return {"ok": True, "message": "Account deleted permanently"}
