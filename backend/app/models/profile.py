import datetime
from sqlalchemy import Column, String, Integer, Boolean, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from app.database import Base

class Profile(Base):
    __tablename__ = "profiles"

    id = Column(String, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    display_name = Column(String, nullable=False, default="Cozy Doubler")
    player_tag = Column(String(8), unique=True, index=True, nullable=False)
    equipped_theme_id = Column(String, ForeignKey("items.id", ondelete="SET NULL"), nullable=True)
    leaves = Column(Integer, default=0, nullable=False)
    total_minutes_focused = Column(Integer, default=0, nullable=False)
    is_supporter = Column(Boolean, default=False, nullable=False)
    fcm_token = Column(String, nullable=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)

    user = relationship("User", back_populates="profile")
    focus_sessions = relationship("FocusSession", back_populates="profile", cascade="all, delete-orphan")
    equipped_theme = relationship("Item", foreign_keys=[equipped_theme_id])
