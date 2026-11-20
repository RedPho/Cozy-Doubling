from typing import List
from pydantic import BaseModel

class BlockUserRequest(BaseModel):
    target_id: str

class ReportUserRequest(BaseModel):
    target_id: str
    reason: str

class BlockedUsersResponse(BaseModel):
    blocked_user_ids: List[str]
