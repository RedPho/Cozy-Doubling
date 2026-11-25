import datetime
from typing import Optional
from sqlalchemy.orm import Session

from app.config import settings
from app.models.profile import Profile
from app.models.session import FocusSession
from app.services.leaf_calc import calculate_verified_rewards


def _utcnow(now: Optional[datetime.datetime] = None) -> datetime.datetime:
    return now if now is not None else datetime.datetime.utcnow()


def _accrue_connected_stretch(
    session: FocusSession,
    now: datetime.datetime,
) -> int:
    """
    Compute verified seconds accrued between session.last_connected_at and now.
    """
    if session.last_connected_at is None or now <= session.last_connected_at:
        return 0
    return max(0, int((now - session.last_connected_at).total_seconds()))


def finalize_session(
    db: Session,
    session: FocusSession,
    tasks_completed: int,
    last_task_text: str,
    now: Optional[datetime.datetime] = None,
    forced_end_reason: Optional[str] = None,
) -> FocusSession:
    """
    Idempotently finalizes a FocusSession using server-verified active time,
    grace windows, abandonment timeouts, and 12-hour max duration cap.
    """
    if session.is_processed:
        return session

    current_time = _utcnow(now)
    grace_seconds = settings.DISCONNECT_GRACE_MINUTES * 60
    abandon_seconds = settings.ABANDON_TIMEOUT_MINUTES * 60
    was_capped = False
    end_reason = forced_end_reason
    end_time = current_time

    if forced_end_reason == "abandoned_disconnect":
        was_capped = True
        end_time = session.last_disconnected_at or session.last_seen_at or session.start_time
    elif session.ever_connected:
        if session.last_connected_at is not None:
            added = _accrue_connected_stretch(session, current_time)
            session.active_seconds = (session.active_seconds or 0) + added
            session.last_connected_at = None
        elif session.last_disconnected_at is not None:
            gap = max(0.0, (current_time - session.last_disconnected_at).total_seconds())
            if gap <= grace_seconds:
                session.active_seconds = (session.active_seconds or 0) + int(gap)
            elif gap > abandon_seconds:
                end_reason = "abandoned_disconnect"
                was_capped = True
                end_time = session.last_disconnected_at
            else:
                # Gap between grace (10m) and abandon timeout (30m): offline gap not paid.
                was_capped = True
    else:
        # Session never connected to /ws/room (e.g., quick REST-only finish or offline orphan)
        elapsed = max(0.0, (current_time - session.start_time).total_seconds())
        if elapsed <= grace_seconds:
            session.active_seconds = int(elapsed)
        else:
            session.active_seconds = 0
            end_reason = "abandoned_disconnect"
            was_capped = True
            end_time = session.start_time

    allow_zero_floor = (end_reason == "abandoned_disconnect")
    duration_mins, earned, hit_max_cap = calculate_verified_rewards(
        verified_seconds=session.active_seconds or 0,
        tasks_completed=tasks_completed,
        max_minutes=settings.MAX_SESSION_DURATION_MINUTES,
        allow_zero_floor=allow_zero_floor,
    )

    if hit_max_cap:
        final_reason = "capped_max_duration"
    elif end_reason is not None:
        final_reason = end_reason
    else:
        final_reason = "completed"

    final_was_capped = bool(was_capped or hit_max_cap or final_reason == "capped_max_duration")

    session.end_time = end_time
    session.tasks_completed = max(0, tasks_completed)
    session.last_task_text = last_task_text
    session.duration_minutes = duration_mins
    session.earned_leaves = earned
    session.is_processed = True
    session.end_reason = final_reason
    session.was_capped = final_was_capped
    session.last_connected_at = None

    profile = db.query(Profile).filter(Profile.id == session.user_id).first()
    if profile:
        profile.leaves += earned
        profile.total_minutes_focused += duration_mins

    db.commit()
    db.refresh(session)
    return session


def record_ws_connect(
    db: Session,
    user_id: str,
    session_id: Optional[str],
    now: Optional[datetime.datetime] = None,
) -> str:
    """
    Records a WebSocket room connection for the given session.
    Returns one of: "no_session", "already_processed", "expired", "ok".
    """
    if not session_id:
        return "no_session"

    session = db.query(FocusSession).filter(
        (FocusSession.id == session_id) & (FocusSession.user_id == user_id)
    ).first()
    if not session:
        return "no_session"
    if session.is_processed:
        return "already_processed"

    current_time = _utcnow(now)
    grace_seconds = settings.DISCONNECT_GRACE_MINUTES * 60
    abandon_seconds = settings.ABANDON_TIMEOUT_MINUTES * 60

    if session.ever_connected and session.last_disconnected_at is not None:
        gap = max(0.0, (current_time - session.last_disconnected_at).total_seconds())
        if gap > abandon_seconds:
            finalize_session(
                db,
                session,
                tasks_completed=session.tasks_completed,
                last_task_text=session.last_task_text or "",
                now=session.last_disconnected_at,
                forced_end_reason="abandoned_disconnect",
            )
            return "expired"
        elif gap <= grace_seconds:
            session.active_seconds = (session.active_seconds or 0) + int(gap)
    elif not session.ever_connected:
        startup_gap = max(0.0, (current_time - session.start_time).total_seconds())
        if startup_gap > abandon_seconds:
            session.active_seconds = 0
            finalize_session(
                db,
                session,
                tasks_completed=session.tasks_completed,
                last_task_text=session.last_task_text or "",
                now=session.start_time,
                forced_end_reason="abandoned_disconnect",
            )
            return "expired"
        session.active_seconds = (session.active_seconds or 0) + int(min(startup_gap, grace_seconds))
        session.ever_connected = True

    session.last_connected_at = current_time
    session.last_disconnected_at = None
    session.last_seen_at = current_time
    db.commit()
    return "ok"


def record_ws_activity(
    db: Session,
    user_id: str,
    session_id: Optional[str],
    tasks_completed: Optional[int] = None,
    active_task_text: Optional[str] = None,
    now: Optional[datetime.datetime] = None,
) -> str:
    """
    Flushes accrued connected seconds and updates heartbeat / task snapshot.
    Returns one of: "no_session", "already_processed", "capped", "ok".
    """
    if not session_id:
        return "no_session"

    session = db.query(FocusSession).filter(
        (FocusSession.id == session_id) & (FocusSession.user_id == user_id)
    ).first()
    if not session:
        return "no_session"
    if session.is_processed:
        return "already_processed"

    current_time = _utcnow(now)
    if session.last_connected_at is not None:
        added = _accrue_connected_stretch(session, current_time)
        session.active_seconds = (session.active_seconds or 0) + added
        session.last_connected_at = current_time

    session.last_seen_at = current_time

    if tasks_completed is not None and tasks_completed >= 0:
        session.tasks_completed = tasks_completed
    if active_task_text and active_task_text != "No active task":
        session.last_task_text = active_task_text

    max_seconds = settings.MAX_SESSION_DURATION_MINUTES * 60
    if (session.active_seconds or 0) >= max_seconds:
        finalize_session(
            db,
            session,
            tasks_completed=session.tasks_completed,
            last_task_text=session.last_task_text or "",
            now=current_time,
            forced_end_reason="capped_max_duration",
        )
        return "capped"

    db.commit()
    return "ok"


def record_ws_disconnect(
    db: Session,
    user_id: str,
    session_id: Optional[str],
    now: Optional[datetime.datetime] = None,
) -> None:
    """
    Flushes connected time up to disconnect and starts the abandonment grace clock.
    """
    if not session_id:
        return

    session = db.query(FocusSession).filter(
        (FocusSession.id == session_id)
        & (FocusSession.user_id == user_id)
        & (FocusSession.is_processed == False)
    ).first()
    if not session:
        return

    current_time = _utcnow(now)
    if session.last_connected_at is not None:
        added = _accrue_connected_stretch(session, current_time)
        session.active_seconds = (session.active_seconds or 0) + added

    session.last_connected_at = None
    session.last_disconnected_at = current_time
    session.last_seen_at = current_time
    db.commit()


def auto_finalize_stale_sessions(
    db: Session,
    user_id: Optional[str] = None,
    now: Optional[datetime.datetime] = None,
) -> int:
    """
    Sweeps unprocessed sessions whose disconnect / heartbeat / never-connected window
    has exceeded ABANDON_TIMEOUT_MINUTES and finalizes them safely.
    """
    current_time = _utcnow(now)
    cutoff = current_time - datetime.timedelta(minutes=settings.ABANDON_TIMEOUT_MINUTES)

    query = db.query(FocusSession).filter(FocusSession.is_processed == False)
    if user_id is not None:
        query = query.filter(FocusSession.user_id == user_id)

    open_sessions = query.all()
    finalized_count = 0

    for s in open_sessions:
        is_stale = False
        if s.last_disconnected_at is not None and s.last_disconnected_at <= cutoff:
            is_stale = True
        elif not s.ever_connected and s.start_time <= cutoff:
            is_stale = True
        elif s.last_connected_at is not None and (s.last_seen_at or s.start_time) <= cutoff:
            seen_end = s.last_seen_at or s.start_time
            added = _accrue_connected_stretch(s, seen_end)
            s.active_seconds = (s.active_seconds or 0) + added
            s.last_connected_at = None
            s.last_disconnected_at = seen_end
            is_stale = True

        if is_stale:
            finalize_session(
                db,
                s,
                tasks_completed=s.tasks_completed,
                last_task_text=s.last_task_text or "",
                now=current_time,
                forced_end_reason="abandoned_disconnect",
            )
            finalized_count += 1

    return finalized_count


def finalize_open_sessions_on_new_start(
    db: Session,
    user_id: str,
    now: Optional[datetime.datetime] = None,
) -> int:
    """
    Enforces the Single Active Session rule when starting a new session:
    auto-finalizes stale sessions and cleanly closes any prior unprocessed session for this user.
    """
    current_time = _utcnow(now)
    count = auto_finalize_stale_sessions(db, user_id=user_id, now=current_time)

    remaining = db.query(FocusSession).filter(
        (FocusSession.user_id == user_id) & (FocusSession.is_processed == False)
    ).all()
    for s in remaining:
        finalize_session(
            db,
            s,
            tasks_completed=s.tasks_completed,
            last_task_text=s.last_task_text or "",
            now=current_time,
        )
        count += 1

    return count
