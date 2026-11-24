import datetime
from sqlalchemy import Column, String, DateTime, ForeignKey, CheckConstraint
from sqlalchemy.orm import relationship
from app.database import Base

class Friendship(Base):
    __tablename__ = "friendships"

    user_id_1 = Column(String, ForeignKey("profiles.id", ondelete="CASCADE"), primary_key=True)
    user_id_2 = Column(String, ForeignKey("profiles.id", ondelete="CASCADE"), primary_key=True)
    requester_id = Column(String, ForeignKey("profiles.id", ondelete="CASCADE"), nullable=False)
    status = Column(String, nullable=False, default="pending")  # 'pending' or 'accepted'
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    accepted_at = Column(DateTime, nullable=True)

    __table_args__ = (
        CheckConstraint("user_id_1 < user_id_2", name="friendships_canonical_user_order"),
        CheckConstraint("status IN ('pending', 'accepted')", name="friendships_status_check"),
    )
