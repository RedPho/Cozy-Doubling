import datetime
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from sqlalchemy import desc

from app.database import get_db
from app.models.user import User
from app.models.profile import Profile
from app.models.friendship import Friendship
from app.models.session import FocusSession
from app.models.safety import UserBlock
from app.schemas.friendship import (
    FriendRequestInput,
    FriendProfileResponse,
    FriendStoryResponse,
)
from app.services.auth import get_current_user_and_profile

router = APIRouter(prefix="/api/friends", tags=["friends"])

def canonical_pair(id1: str, id2: str) -> tuple[str, str]:
    return (id1, id2) if id1 < id2 else (id2, id1)

def get_blocked_user_ids(user_id: str, db: Session) -> set[str]:
    blocks = db.query(UserBlock).filter(
        (UserBlock.blocker_id == user_id) | (UserBlock.blocked_id == user_id)
    ).all()
    return {
        b.blocked_id if b.blocker_id == user_id else b.blocker_id
        for b in blocks
    }

@router.post("/request")
def send_friend_request(
    req: FriendRequestInput,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id
    target_tag = req.player_tag.strip().upper().lstrip("#")

    target = db.query(Profile).filter(Profile.player_tag == target_tag).first()
    if not target:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="User not found"
        )

    if target.id == my_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You can't add yourself!"
        )

    u1, u2 = canonical_pair(my_id, target.id)
    existing = db.query(Friendship).filter(
        (Friendship.user_id_1 == u1) & (Friendship.user_id_2 == u2)
    ).first()

    if existing:
        if existing.status == "accepted":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Already friends!"
            )
        if existing.requester_id == my_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Request already sent!"
            )
        # Mutual auto-acceptance
        existing.status = "accepted"
        existing.accepted_at = datetime.datetime.utcnow()
        db.commit()
        return {"ok": True, "message": "Friend request accepted!"}

    new_friendship = Friendship(
        user_id_1=u1,
        user_id_2=u2,
        requester_id=my_id,
        status="pending"
    )
    db.add(new_friendship)
    db.commit()
    return {"ok": True, "message": "Friend request sent!"}

@router.post("/{sender_id}/accept")
def accept_friend(
    sender_id: str,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id
    u1, u2 = canonical_pair(my_id, sender_id)

    f = db.query(Friendship).filter(
        (Friendship.user_id_1 == u1) &
        (Friendship.user_id_2 == u2) &
        (Friendship.requester_id == sender_id) &
        (Friendship.status == "pending")
    ).first()

    if not f:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friend request not found"
        )

    f.status = "accepted"
    f.accepted_at = datetime.datetime.utcnow()
    db.commit()
    return {"ok": True, "message": "Friend request accepted"}

@router.post("/{sender_id}/reject")
def reject_friend(
    sender_id: str,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id
    u1, u2 = canonical_pair(my_id, sender_id)

    db.query(Friendship).filter(
        (Friendship.user_id_1 == u1) &
        (Friendship.user_id_2 == u2) &
        (Friendship.requester_id == sender_id) &
        (Friendship.status == "pending")
    ).delete()
    db.commit()
    return {"ok": True, "message": "Friend request rejected"}

@router.delete("/{friend_id}")
def unfriend(
    friend_id: str,
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id
    u1, u2 = canonical_pair(my_id, friend_id)

    db.query(Friendship).filter(
        (Friendship.user_id_1 == u1) &
        (Friendship.user_id_2 == u2) &
        (Friendship.status == "accepted")
    ).delete()
    db.commit()
    return {"ok": True, "message": "Friend removed"}

@router.get("", response_model=List[FriendStoryResponse])
def list_friends(
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id
    blocked_ids = get_blocked_user_ids(my_id, db)

    friendships = db.query(Friendship).filter(
        (Friendship.status == "accepted") &
        ((Friendship.user_id_1 == my_id) | (Friendship.user_id_2 == my_id))
    ).all()

    friends_data: List[FriendStoryResponse] = []
    for f in friendships:
        friend_id = f.user_id_2 if f.user_id_1 == my_id else f.user_id_1
        if friend_id in blocked_ids:
            continue

        friend_profile = db.query(Profile).filter(Profile.id == friend_id).first()
        if not friend_profile:
            continue

        latest_session = (
            db.query(FocusSession)
            .filter((FocusSession.user_id == friend_id) & (FocusSession.is_processed == True))
            .order_by(desc(FocusSession.end_time))
            .first()
        )

        friends_data.append(FriendStoryResponse(
            friend_id=friend_id,
            display_name=friend_profile.display_name,
            player_tag=friend_profile.player_tag,
            duration_minutes=latest_session.duration_minutes if latest_session else None,
            end_time=latest_session.end_time.isoformat() if latest_session and latest_session.end_time else None,
            last_task_text=latest_session.last_task_text if latest_session else None,
            accepted_at=f.accepted_at.isoformat() if f.accepted_at else None
        ))

    return friends_data

@router.get("/requests", response_model=List[FriendProfileResponse])
def list_incoming_requests(
    user_and_profile: tuple[User, Profile] = Depends(get_current_user_and_profile),
    db: Session = Depends(get_db)
):
    _, profile = user_and_profile
    my_id = profile.id
    blocked_ids = get_blocked_user_ids(my_id, db)

    pending = db.query(Friendship).filter(
        (Friendship.status == "pending") &
        (Friendship.requester_id != my_id) &
        ((Friendship.user_id_1 == my_id) | (Friendship.user_id_2 == my_id))
    ).all()

    requests_data: List[FriendProfileResponse] = []
    for f in pending:
        sender_id = f.requester_id
        if sender_id in blocked_ids:
            continue
        sender_profile = db.query(Profile).filter(Profile.id == sender_id).first()
        if sender_profile:
            requests_data.append(FriendProfileResponse(
                id=sender_profile.id,
                display_name=sender_profile.display_name,
                player_tag=sender_profile.player_tag
            ))

    return requests_data
