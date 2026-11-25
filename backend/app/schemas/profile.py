from typing import Optional
import datetime
from pydantic import BaseModel, field_validator

class ProfileResponse(BaseModel):
    id: str
    display_name: str
    player_tag: str
    equipped_theme_id: Optional[str] = None
    leaves: int = 0
    total_minutes_focused: int = 0
    is_supporter: bool = False
    fcm_token: Optional[str] = None
    created_at: Optional[str] = None

    class Config:
        from_attributes = True

    @classmethod
    def from_orm_model(cls, p):
        return cls(
            id=p.id,
            display_name=p.display_name,
            player_tag=p.player_tag,
            equipped_theme_id=p.equipped_theme_id,
            leaves=p.leaves,
            total_minutes_focused=p.total_minutes_focused,
            is_supporter=p.is_supporter,
            fcm_token=p.fcm_token,
            created_at=p.created_at.isoformat() if isinstance(p.created_at, datetime.datetime) else str(p.created_at) if p.created_at else None
        )

class ProfileUpdateRequest(BaseModel):
    display_name: str

    @field_validator("display_name")
    @classmethod
    def validate_name(cls, v: str) -> str:
        clean = v.strip()
        if not clean:
            raise ValueError("Display name cannot be blank or whitespace-only")
        if len(clean) > 30:
            raise ValueError("Display name must be at most 30 characters")
        return clean

class FcmTokenUpdateRequest(BaseModel):
    fcm_token: Optional[str] = None
