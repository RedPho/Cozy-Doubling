import datetime
import uuid
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.user import User
from app.models.profile import Profile
from app.models.session import FocusSession
from app.models.friendship import Friendship
from app.models.safety import UserBlock
from app.schemas.session import (
    StartSessionResponse,
    FinishSessionRequest,
    FocusSessionResponse,
)
from app.services.auth import get_current_user_and_profile
from app.services.notification import send_friend_focus_notification
from app.services.session_lifecycle import (
    auto_finalize_stale_sessions,
    finalize_open_sessions_on_new_start,
    finalize_session,
)

router = APIRouter(prefix="/api/sessions", tags=["sessions"])

@router.post("/start", response_model=StartSessionResponse)
def start_session(
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    user, profile = user_and_profile
    now = datetime.datetime.utcnow()

    # Enforce single active session: finalize any prior open/stale sessions for this user
    finalize_open_sessions_on_new_start(db, profile.id, now=now)

    session_id = str(uuid.uuid4())
    session = FocusSession(
        id=session_id,
        user_id=profile.id,
        start_time=now,
        last_seen_at=now,
        last_activity_at=now,
        is_processed=False
    )
    db.add(session)
    db.commit()

    # Find accepted friends
    my_id = profile.id
    friend_rows = db.query(Friendship).filter(
        (Friendship.status == "accepted") &
        ((Friendship.user_id_1 == my_id) | (Friendship.user_id_2 == my_id))
    ).all()

    friend_ids = [
        f.user_id_2 if f.user_id_1 == my_id else f.user_id_1
        for f in friend_rows
    ]

    if friend_ids:
        # Check blocked
        blocked_rows = db.query(UserBlock).filter(
            ((UserBlock.blocker_id == my_id) & (UserBlock.blocked_id.in_(friend_ids))) |
            ((UserBlock.blocked_id == my_id) & (UserBlock.blocker_id.in_(friend_ids)))
        ).all()
        blocked_set = {
            b.blocked_id if b.blocker_id == my_id else b.blocker_id
            for b in blocked_rows
        }
        eligible_friend_ids = [fid for fid in friend_ids if fid not in blocked_set]

        if eligible_friend_ids:
            profiles = db.query(Profile.fcm_token).filter(
                (Profile.id.in_(eligible_friend_ids)) &
                (Profile.fcm_token.isnot(None))
            ).all()
            tokens = [t[0] for t in profiles if t[0]]
            if tokens:
                import asyncio
                try:
                    loop = asyncio.get_running_loop()
                    loop.create_task(send_friend_focus_notification(profile.display_name, tokens))
                except RuntimeError:
                    pass

    return StartSessionResponse(session_id=session_id)

@router.post("/{session_id}/finish", response_model=FocusSessionResponse)
def finish_session(
    session_id: str,
    req: FinishSessionRequest,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    session = db.query(FocusSession).filter(
        (FocusSession.id == session_id) &
        (FocusSession.user_id == profile.id)
    ).first()

    forced_reason = "app_closed" if req.end_reason == "app_closed" else None

    if not session:
        session = FocusSession(
            id=session_id,
            user_id=profile.id,
            start_time=datetime.datetime.utcnow(),
            is_processed=False
        )
        db.add(session)
    elif session.is_processed:
        if forced_reason == "app_closed":
            return FocusSessionResponse.from_orm_model(session)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Session already processed."
        )

    end_time = datetime.datetime.utcnow()
    session = finalize_session(
        db=db,
        session=session,
        tasks_completed=req.tasks_completed,
        last_task_text=req.last_task_text,
        now=end_time,
        forced_end_reason=forced_reason,
    )

    return FocusSessionResponse.from_orm_model(session)

@router.get("/{session_id}", response_model=FocusSessionResponse)
def get_session(
    session_id: str,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    auto_finalize_stale_sessions(db, user_id=profile.id)

    session = db.query(FocusSession).filter(
        (FocusSession.id == session_id) &
        (FocusSession.user_id == profile.id)
    ).first()

    if not session:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found"
        )

    return FocusSessionResponse.from_orm_model(session)
