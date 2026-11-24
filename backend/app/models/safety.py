import datetime
import uuid
from sqlalchemy import Column, String, DateTime, ForeignKey
from app.database import Base

class UserBlock(Base):
    __tablename__ = "user_blocks"

    blocker_id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    blocked_id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)

class UserReport(Base):
    __tablename__ = "user_reports"

    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    reporter_id = Column(String, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    reported_id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    reason = Column(String, nullable=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
