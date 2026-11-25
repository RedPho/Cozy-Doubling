from typing import List
from pydantic import BaseModel, Field

class BlockUserRequest(BaseModel):
    target_id: str

class ReportUserRequest(BaseModel):
    target_id: str
    reason: str = Field(..., min_length=1, max_length=200)

class BlockedUsersResponse(BaseModel):
    blocked_user_ids: List[str]


class BlockedProfileResponse(BaseModel):
    id: str
    display_name: str
    player_tag: str
