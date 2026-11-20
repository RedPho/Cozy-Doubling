import datetime
import uuid
from sqlalchemy import Column, String, Integer, Boolean, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from app.database import Base

class FocusSession(Base):
    __tablename__ = "focus_sessions"

    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String, ForeignKey("profiles.id", ondelete="CASCADE"), nullable=False, index=True)
    start_time = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    end_time = Column(DateTime, nullable=True)
    tasks_completed = Column(Integer, default=0, nullable=False)
    earned_leaves = Column(Integer, default=0, nullable=False)
    is_processed = Column(Boolean, default=False, nullable=False, index=True)
    duration_minutes = Column(Integer, default=0, nullable=False)
    last_task_text = Column(String, nullable=True)

    profile = relationship("Profile", back_populates="focus_sessions")
