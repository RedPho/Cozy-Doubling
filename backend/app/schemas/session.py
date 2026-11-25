from typing import Optional
import datetime
from pydantic import BaseModel

class StartSessionResponse(BaseModel):
    session_id: str

class FinishSessionRequest(BaseModel):
    tasks_completed: int
    last_task_text: str = ""
    end_reason: Optional[str] = None

class FocusSessionResponse(BaseModel):
    id: str
    user_id: str
    start_time: str
    end_time: Optional[str] = None
    tasks_completed: int = 0
    earned_leaves: int = 0
    is_processed: bool = False
    duration_minutes: int = 0
    last_task_text: Optional[str] = None
    end_reason: Optional[str] = None
    was_capped: bool = False

    @classmethod
    def from_orm_model(cls, s):
        return cls(
            id=s.id,
            user_id=s.user_id,
            start_time=s.start_time.isoformat() if isinstance(s.start_time, datetime.datetime) else str(s.start_time),
            end_time=s.end_time.isoformat() if isinstance(s.end_time, datetime.datetime) else str(s.end_time) if s.end_time else None,
            tasks_completed=s.tasks_completed,
            earned_leaves=s.earned_leaves,
            is_processed=s.is_processed,
            duration_minutes=s.duration_minutes,
            last_task_text=s.last_task_text,
            end_reason=getattr(s, "end_reason", None),
            was_capped=bool(getattr(s, "was_capped", False)),
        )
