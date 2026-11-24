import datetime

def calculate_session_rewards(
    start_time: datetime.datetime,
    end_time: datetime.datetime,
    tasks_completed: int
) -> tuple[int, int]:
    """
    Server-side anti-cheat duration and leaf reward calculation.
    
    Formula:
    duration_minutes = (end_time - start_time) in minutes (rounded down to whole minutes)
    earned_leaves = (duration_minutes * 1) + (tasks_completed * 3)
    minimum floor = 1 leaf
    
    Returns: (duration_minutes, earned_leaves)
    """
    total_seconds = max(0.0, (end_time - start_time).total_seconds())
    duration_minutes = int(total_seconds // 60)
    
    earned_leaves = (duration_minutes * 1) + (max(0, tasks_completed) * 3)
    earned_leaves = max(1, earned_leaves)
    
    return duration_minutes, earned_leaves
