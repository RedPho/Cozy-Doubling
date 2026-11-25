import datetime
from app.config import settings


def calculate_verified_rewards(
    verified_seconds: int,
    tasks_completed: int,
    max_minutes: int = settings.MAX_SESSION_DURATION_MINUTES,
    allow_zero_floor: bool = False,
) -> tuple[int, int, bool]:
    """
    Server-side anti-cheat duration and leaf reward calculation from verified active seconds.

    Formula:
    raw_minutes = floor(verified_seconds / 60)
    duration_minutes = min(raw_minutes, max_minutes)
    earned_leaves = (duration_minutes * 1) + (tasks_completed * 3)
    Minimum floor = 1 leaf (unless allow_zero_floor=True and raw_leaves == 0 for empty abandoned sessions).

    Returns: (duration_minutes, earned_leaves, was_capped)
    """
    raw_minutes = max(0, int(max(0, verified_seconds) // 60))
    was_capped = raw_minutes > max_minutes
    duration_minutes = min(raw_minutes, max_minutes)

    raw_leaves = (duration_minutes * 1) + (max(0, tasks_completed) * 3)
    if allow_zero_floor and raw_leaves == 0:
        earned_leaves = 0
    else:
        earned_leaves = max(1, raw_leaves)

    return duration_minutes, earned_leaves, was_capped


def calculate_session_rewards(
    start_time: datetime.datetime,
    end_time: datetime.datetime,
    tasks_completed: int
) -> tuple[int, int]:
    """
    Backwards-compatible helper deriving verified rewards from timestamp delta
    capped at MAX_SESSION_DURATION_MINUTES.
    """
    total_seconds = int(max(0.0, (end_time - start_time).total_seconds()))
    duration_minutes, earned_leaves, _ = calculate_verified_rewards(
        verified_seconds=total_seconds,
        tasks_completed=tasks_completed,
        max_minutes=settings.MAX_SESSION_DURATION_MINUTES,
        allow_zero_floor=False,
    )
    return duration_minutes, earned_leaves
