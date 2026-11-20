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
from app.services.leaf_calc import calculate_session_rewards
from app.services.notification import send_friend_focus_notification

router = APIRouter(prefix="/api/sessions", tags=["sessions"])

@router.post("/start", response_model=StartSessionResponse)
def start_session(
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    user, profile = user_and_profile
    session_id = str(uuid.uuid4())
    session = FocusSession(
        id=session_id,
        user_id=profile.id,
        start_time=datetime.datetime.utcnow(),
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

    if not session or session.is_processed:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Session not found or already processed."
        )

    end_time = datetime.datetime.utcnow()
    duration_mins, earned = calculate_session_rewards(session.start_time, end_time, req.tasks_completed)

    session.end_time = end_time
    session.tasks_completed = req.tasks_completed
    session.last_task_text = req.last_task_text
    session.duration_minutes = duration_mins
    session.earned_leaves = earned
    session.is_processed = True

    # Atomically update profile
    profile.leaves += earned
    profile.total_minutes_focused += duration_mins

    db.commit()
    db.refresh(session)

    return FocusSessionResponse.from_orm_model(session)

@router.get("/{session_id}", response_model=FocusSessionResponse)
def get_session(
    session_id: str,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
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
