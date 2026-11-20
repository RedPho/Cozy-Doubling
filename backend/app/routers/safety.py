import asyncio
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.user import User
from app.models.profile import Profile
from app.models.safety import UserBlock, UserReport
from app.schemas.safety import (
    BlockUserRequest,
    ReportUserRequest,
    BlockedUsersResponse,
)
from app.services.auth import get_current_user_and_profile
from app.websockets.safety_manager import safety_manager

router = APIRouter(prefix="/api/safety", tags=["safety"])

def fire_and_forget(coro):
    try:
        loop = asyncio.get_running_loop()
        loop.create_task(coro)
    except RuntimeError:
        # If no loop is running, close the unawaited coroutine cleanly to prevent warnings
        coro.close()

@router.post("/block")
def block_user(
    req: BlockUserRequest,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id
    target_id = req.target_id

    if my_id == target_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You cannot block yourself."
        )

    existing = db.query(UserBlock).filter(
        (UserBlock.blocker_id == my_id) & (UserBlock.blocked_id == target_id)
    ).first()
    if not existing:
        db.add(UserBlock(blocker_id=my_id, blocked_id=target_id))
        db.commit()

    fire_and_forget(safety_manager.notify_block_change(my_id, {
        "type": "block_added",
        "blocked_id": target_id
    }))

    return {"ok": True, "message": "User blocked"}

@router.post("/unblock")
def unblock_user(
    req: BlockUserRequest,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id
    target_id = req.target_id

    db.query(UserBlock).filter(
        (UserBlock.blocker_id == my_id) & (UserBlock.blocked_id == target_id)
    ).delete()
    db.commit()

    fire_and_forget(safety_manager.notify_block_change(my_id, {
        "type": "block_removed",
        "blocked_id": target_id
    }))

    return {"ok": True, "message": "User unblocked"}

@router.get("/blocks", response_model=BlockedUsersResponse)
def get_blocked_users(
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id

    blocks = db.query(UserBlock).filter(
        (UserBlock.blocker_id == my_id) | (UserBlock.blocked_id == my_id)
    ).all()

    all_ids = {
        b.blocked_id if b.blocker_id == my_id else b.blocker_id
        for b in blocks
    }
    return BlockedUsersResponse(blocked_user_ids=list(all_ids))

@router.post("/report")
def report_user(
    req: ReportUserRequest,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id
    target_id = req.target_id
    clean_reason = req.reason.strip()

    if not clean_reason:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Report reason cannot be empty."
        )

    report = UserReport(
        reporter_id=my_id,
        reported_id=target_id,
        reason=clean_reason
    )
    db.add(report)
    db.commit()

    return {"ok": True, "message": "Report submitted. Thank you."}
