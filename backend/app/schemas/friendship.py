from typing import Optional
from pydantic import BaseModel, Field

class FriendRequestInput(BaseModel):
    player_tag: str = Field(..., min_length=1, max_length=16)

class FriendProfileResponse(BaseModel):
    id: str
    display_name: str
    player_tag: str

class FriendStoryResponse(BaseModel):
    friend_id: str
    display_name: str
    player_tag: str
    duration_minutes: Optional[int] = None
    end_time: Optional[str] = None
    last_task_text: Optional[str] = None
    accepted_at: Optional[str] = None
