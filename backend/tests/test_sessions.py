import datetime
from starlette.testclient import TestClient
from app.database import get_db
from app.main import app
from app.models.session import FocusSession
from app.services.session_lifecycle import (
    record_ws_activity,
    record_ws_connect,
    record_ws_disconnect,
)
from tests.conftest import create_user_helper


def _get_test_db():
    dep = app.dependency_overrides[get_db]
    gen = dep()
    return gen, next(gen)

def test_session_lifecycle_and_leaf_rewards(client: TestClient):
    user = create_user_helper(client, "focuser@cozy.com")
    headers = user["headers"]

    # Start session
    start_resp = client.post("/api/sessions/start", headers=headers)
    assert start_resp.status_code == 200
    session_id = start_resp.json()["session_id"]
    assert session_id is not None

    # Finish session with 3 tasks completed
    finish_resp = client.post(f"/api/sessions/{session_id}/finish", headers=headers, json={
        "tasks_completed": 3,
        "last_task_text": "Completed task 3"
    })
    assert finish_resp.status_code == 200
    session = finish_resp.json()
    assert session["tasks_completed"] == 3
    assert session["is_processed"] is True
    # Anti-cheat formula: earned = max(1, duration*1 + tasks*3) => 0*1 + 3*3 = 9 leaves
    assert session["earned_leaves"] == 9
    assert session["last_task_text"] == "Completed task 3"

    # Verify profile leaves balance incremented
    profile_resp = client.get("/api/profile", headers=headers)
    assert profile_resp.json()["leaves"] == 9

    # Re-finishing an already finalized session must fail
    refinish_resp = client.post(f"/api/sessions/{session_id}/finish", headers=headers, json={
        "tasks_completed": 3,
        "last_task_text": "Completed task 3"
    })
    assert refinish_resp.status_code == 400
    assert "already processed" in refinish_resp.json()["detail"].lower()

def test_session_minimum_reward_floor(client: TestClient):
    user = create_user_helper(client, "quick_focuser@cozy.com")
    headers = user["headers"]

    start_resp = client.post("/api/sessions/start", headers=headers)
    session_id = start_resp.json()["session_id"]

    finish_resp = client.post(f"/api/sessions/{session_id}/finish", headers=headers, json={
        "tasks_completed": 0,
        "last_task_text": ""
    })
    assert finish_resp.status_code == 200
    assert finish_resp.json()["earned_leaves"] == 1


def test_twelve_hour_uninterrupted_marathon_earns_full_credits(client: TestClient):
    user = create_user_helper(client, "marathon12h@cozy.com")
    headers = user["headers"]
    uid = user["profile"]["id"]
    sid = client.post("/api/sessions/start", headers=headers).json()["session_id"]

    gen, db = _get_test_db()
    try:
        s = db.query(FocusSession).filter(FocusSession.id == sid).first()
        t0 = s.start_time
        assert record_ws_connect(db, uid, sid, now=t0) == "ok"
        # Passive heartbeats every 3 hours (no clicks or check-ins needed) up to 12 hours
        for hours in (3, 6, 9, 12):
            res = record_ws_activity(
                db, uid, sid,
                now=t0 + datetime.timedelta(hours=hours)
            )
            if hours < 12:
                assert res == "ok"
        session = db.query(FocusSession).filter(FocusSession.id == sid).first()
        assert session.is_processed is True
        assert session.duration_minutes == 720
        assert session.was_capped is True
        assert session.end_reason == "capped_max_duration"
    finally:
        gen.close()

    # Honest 11h59m uninterrupted marathon finishes normally without hitting cap
    sid2 = client.post("/api/sessions/start", headers=headers).json()["session_id"]
    gen, db = _get_test_db()
    try:
        s2 = db.query(FocusSession).filter(FocusSession.id == sid2).first()
        t0 = s2.start_time
        assert record_ws_connect(db, uid, sid2, now=t0) == "ok"
        from app.services.session_lifecycle import finalize_session
        done = finalize_session(
            db, s2, tasks_completed=2, last_task_text="Thesis",
            now=t0 + datetime.timedelta(hours=11, minutes=59)
        )
        assert done.duration_minutes == 719
        assert done.earned_leaves == 719 + 6
        assert done.was_capped is False
        assert done.end_reason == "completed"
    finally:
        gen.close()


def test_eighteen_hour_connected_session_capped_at_twelve_hours(client: TestClient):
    user = create_user_helper(client, "cap18h@cozy.com")
    headers = user["headers"]
    uid = user["profile"]["id"]
    sid = client.post("/api/sessions/start", headers=headers).json()["session_id"]

    gen, db = _get_test_db()
    try:
        s = db.query(FocusSession).filter(FocusSession.id == sid).first()
        t0 = s.start_time
        record_ws_connect(db, uid, sid, now=t0)
        res = record_ws_activity(db, uid, sid, now=t0 + datetime.timedelta(hours=18))
        assert res == "capped"
        done = db.query(FocusSession).filter(FocusSession.id == sid).first()
        assert done.duration_minutes == 720
        assert done.was_capped is True
        assert done.end_reason == "capped_max_duration"
    finally:
        gen.close()


def test_brief_disconnect_within_grace_loses_zero_time(client: TestClient):
    user = create_user_helper(client, "grace@cozy.com")
    headers = user["headers"]
    uid = user["profile"]["id"]
    sid = client.post("/api/sessions/start", headers=headers).json()["session_id"]

    gen, db = _get_test_db()
    try:
        s = db.query(FocusSession).filter(FocusSession.id == sid).first()
        t0 = s.start_time
        record_ws_connect(db, uid, sid, now=t0)
        record_ws_disconnect(db, uid, sid, now=t0 + datetime.timedelta(minutes=20))
        # Reconnect after 5 minutes (<= 10m grace)
        assert record_ws_connect(db, uid, sid, now=t0 + datetime.timedelta(minutes=25)) == "ok"
        from app.services.session_lifecycle import finalize_session
        done = finalize_session(
            db, s, tasks_completed=1, last_task_text="Reading",
            now=t0 + datetime.timedelta(minutes=40)
        )
        assert done.duration_minutes == 40
        assert done.earned_leaves == 43
        assert done.was_capped is False
        assert done.end_reason == "completed"
    finally:
        gen.close()


def test_medium_disconnect_resumes_without_paying_offline_gap(client: TestClient):
    user = create_user_helper(client, "medgap@cozy.com")
    headers = user["headers"]
    uid = user["profile"]["id"]
    sid = client.post("/api/sessions/start", headers=headers).json()["session_id"]

    gen, db = _get_test_db()
    try:
        s = db.query(FocusSession).filter(FocusSession.id == sid).first()
        t0 = s.start_time
        record_ws_connect(db, uid, sid, now=t0)
        record_ws_disconnect(db, uid, sid, now=t0 + datetime.timedelta(minutes=20))
        # Reconnect after 20 minutes (10m < gap <= 30m)
        assert record_ws_connect(db, uid, sid, now=t0 + datetime.timedelta(minutes=40)) == "ok"
        from app.services.session_lifecycle import finalize_session
        done = finalize_session(
            db, s, tasks_completed=0, last_task_text="Code",
            now=t0 + datetime.timedelta(minutes=50)
        )
        # Paid 20m before + 10m after = 30m (20m gap not paid)
        assert done.duration_minutes == 30
        assert done.end_reason == "completed_with_offline_gap"
        assert done.was_capped is True
    finally:
        gen.close()

    # Case 2: Finish directly after a 15-minute disconnect without reconnecting -> gap excluded & offline gap labeled
    sid2 = client.post("/api/sessions/start", headers=headers).json()["session_id"]
    gen, db = _get_test_db()
    try:
        s2 = db.query(FocusSession).filter(FocusSession.id == sid2).first()
        t0 = s2.start_time
        record_ws_connect(db, uid, sid2, now=t0)
        record_ws_disconnect(db, uid, sid2, now=t0 + datetime.timedelta(minutes=25))
        done2 = finalize_session(
            db, s2, tasks_completed=1, last_task_text="Finish after offline gap",
            now=t0 + datetime.timedelta(minutes=45)
        )
        assert done2.duration_minutes == 25
        assert done2.earned_leaves == 28
        assert done2.end_reason == "completed_with_offline_gap"
        assert done2.was_capped is True
    finally:
        gen.close()


def test_late_ws_startup_beyond_grace_marks_offline_gap(client: TestClient):
    user = create_user_helper(client, "latestartup@cozy.com")
    headers = user["headers"]
    uid = user["profile"]["id"]
    sid = client.post("/api/sessions/start", headers=headers).json()["session_id"]

    gen, db = _get_test_db()
    try:
        s = db.query(FocusSession).filter(FocusSession.id == sid).first()
        t0 = s.start_time
        # Connect 15m after startSession (beyond 10m grace, within 30m abandon): caps startup at 10m
        assert record_ws_connect(db, uid, sid, now=t0 + datetime.timedelta(minutes=15)) == "ok"
        from app.services.session_lifecycle import finalize_session
        done = finalize_session(
            db, s, tasks_completed=0, last_task_text="Late WS connect",
            now=t0 + datetime.timedelta(minutes=25)
        )
        assert done.duration_minutes == 20
        assert done.end_reason == "completed_with_offline_gap"
        assert done.was_capped is True
    finally:
        gen.close()


def test_abandoned_early_leave_auto_finalizes_and_zeros_empty_abandoned(client: TestClient):
    # Case A: Connected 25m, left and gone 3 hours -> gets 25m, abandoned_disconnect
    user_a = create_user_helper(client, "abandon_a@cozy.com")
    uid_a = user_a["profile"]["id"]
    sid_a = client.post("/api/sessions/start", headers=user_a["headers"]).json()["session_id"]

    gen, db = _get_test_db()
    try:
        s_a = db.query(FocusSession).filter(FocusSession.id == sid_a).first()
        t0 = s_a.start_time
        record_ws_connect(db, uid_a, sid_a, now=t0)
        record_ws_disconnect(db, uid_a, sid_a, now=t0 + datetime.timedelta(minutes=25))
        # Backdate timestamps by 3 hours so GET /api/sessions/{sid_a} sweeps using real utcnow()
        s_a.start_time -= datetime.timedelta(hours=3)
        s_a.last_disconnected_at -= datetime.timedelta(hours=3)
        s_a.last_seen_at -= datetime.timedelta(hours=3)
        db.commit()
    finally:
        gen.close()

    resp_a = client.get(f"/api/sessions/{sid_a}", headers=user_a["headers"])
    assert resp_a.status_code == 200
    data_a = resp_a.json()
    assert data_a["is_processed"] is True
    assert data_a["duration_minutes"] == 25
    assert data_a["earned_leaves"] == 25
    assert data_a["end_reason"] == "abandoned_disconnect"
    assert data_a["was_capped"] is True

    # Case B: Started and left immediately (0m, 0 tasks), gone 1 hour -> 0 leaves!
    user_b = create_user_helper(client, "abandon_empty@cozy.com")
    uid_b = user_b["profile"]["id"]
    sid_b = client.post("/api/sessions/start", headers=user_b["headers"]).json()["session_id"]
    gen, db = _get_test_db()
    try:
        s_b = db.query(FocusSession).filter(FocusSession.id == sid_b).first()
        t0 = s_b.start_time
        record_ws_connect(db, uid_b, sid_b, now=t0)
        record_ws_disconnect(db, uid_b, sid_b, now=t0 + datetime.timedelta(seconds=5))
        s_b.start_time -= datetime.timedelta(hours=1)
        s_b.last_disconnected_at -= datetime.timedelta(hours=1)
        s_b.last_seen_at -= datetime.timedelta(hours=1)
        db.commit()
    finally:
        gen.close()

    resp_b = client.get(f"/api/sessions/{sid_b}", headers=user_b["headers"]).json()
    assert resp_b["is_processed"] is True
    assert resp_b["duration_minutes"] == 0
    assert resp_b["earned_leaves"] == 0
    assert resp_b["end_reason"] == "abandoned_disconnect"


def test_five_hour_uninterrupted_background_session_earns_full_credits(client: TestClient):
    user = create_user_helper(client, "fivehours@cozy.com")
    uid = user["profile"]["id"]
    sid = client.post("/api/sessions/start", headers=user["headers"]).json()["session_id"]

    gen, db = _get_test_db()
    try:
        s = db.query(FocusSession).filter(FocusSession.id == sid).first()
        t0 = s.start_time
        record_ws_connect(db, uid, sid, now=t0)
        # Only passive heartbeats (ping) for 5 hours, zero clicks or check-ins required
        for h in range(1, 5):
            record_ws_activity(db, uid, sid, now=t0 + datetime.timedelta(hours=h))
        from app.services.session_lifecycle import finalize_session
        done = finalize_session(
            db, s, tasks_completed=1, last_task_text="Deep work in background",
            now=t0 + datetime.timedelta(hours=5)
        )
        assert done.duration_minutes == 300
        assert done.earned_leaves == 303
        assert done.end_reason == "completed"
        assert done.was_capped is False
    finally:
        gen.close()


def test_never_connected_late_finish_grants_zero_minutes(client: TestClient):
    user = create_user_helper(client, "neverws@cozy.com")
    sid = client.post("/api/sessions/start", headers=user["headers"]).json()["session_id"]

    gen, db = _get_test_db()
    try:
        s = db.query(FocusSession).filter(FocusSession.id == sid).first()
        s.start_time -= datetime.timedelta(hours=4)
        db.commit()
    finally:
        gen.close()

    finish = client.post(f"/api/sessions/{sid}/finish", headers=user["headers"], json={
        "tasks_completed": 0,
        "last_task_text": "Never joined room"
    }).json()
    assert finish["duration_minutes"] == 0
    assert finish["earned_leaves"] == 0
    assert finish["end_reason"] == "abandoned_disconnect"


def test_starting_new_session_auto_finalizes_previous(client: TestClient):
    user = create_user_helper(client, "singleactive@cozy.com")
    uid = user["profile"]["id"]
    sid1 = client.post("/api/sessions/start", headers=user["headers"]).json()["session_id"]

    gen, db = _get_test_db()
    try:
        s1 = db.query(FocusSession).filter(FocusSession.id == sid1).first()
        t0 = s1.start_time
        record_ws_connect(db, uid, sid1, now=t0)
        record_ws_activity(db, uid, sid1, now=t0 + datetime.timedelta(minutes=15))
    finally:
        gen.close()

    # Starting Session 2 must auto-finalize Session 1
    sid2 = client.post("/api/sessions/start", headers=user["headers"]).json()["session_id"]
    assert sid2 != sid1

    s1_resp = client.get(f"/api/sessions/{sid1}", headers=user["headers"]).json()
    assert s1_resp["is_processed"] is True
    assert s1_resp["duration_minutes"] >= 15


def test_session_finish_with_app_closed_reason_saves_verified_time_without_offline_gap(client: TestClient):
    user = create_user_helper(client, "appclosed@cozy.com")
    headers = user["headers"]
    uid = user["profile"]["id"]
    sid = client.post("/api/sessions/start", headers=headers).json()["session_id"]

    gen, db = _get_test_db()
    try:
        s = db.query(FocusSession).filter(FocusSession.id == sid).first()
        t0 = s.start_time
        assert record_ws_connect(db, uid, sid, now=t0) == "ok"
        # Connect for 15 minutes, then disconnect for 8 minutes before user re-opens app
        record_ws_disconnect(db, uid, sid, now=t0 + datetime.timedelta(minutes=15))
        s = db.query(FocusSession).filter(FocusSession.id == sid).first()
        s.start_time -= datetime.timedelta(minutes=23)
        s.last_disconnected_at -= datetime.timedelta(minutes=8)
        s.last_seen_at -= datetime.timedelta(minutes=8)
        db.commit()
    finally:
        gen.close()

    finish_resp = client.post(
        f"/api/sessions/{sid}/finish",
        headers=headers,
        json={
            "tasks_completed": 1,
            "last_task_text": "Read chapter 2",
            "end_reason": "app_closed",
        },
    )
    assert finish_resp.status_code == 200
    data = finish_resp.json()
    assert data["is_processed"] is True
    assert data["duration_minutes"] == 15
    assert data["earned_leaves"] == 18
    assert data["end_reason"] == "app_closed"
    assert data["was_capped"] is False

    # Re-sending app_closed recovery finish must be idempotent without double-crediting leaves
    dup_resp = client.post(
        f"/api/sessions/{sid}/finish",
        headers=headers,
        json={
            "tasks_completed": 1,
            "last_task_text": "Read chapter 2",
            "end_reason": "app_closed",
        },
    )
    assert dup_resp.status_code == 200
    assert dup_resp.json()["earned_leaves"] == 18
    profile_resp = client.get("/api/profile", headers=headers).json()
    assert profile_resp["leaves"] == 18

    # Immediate 0-minute / 0-task closed session yields 0 leaves instead of 1-leaf floor
    sid_empty = client.post("/api/sessions/start", headers=headers).json()["session_id"]
    empty_resp = client.post(
        f"/api/sessions/{sid_empty}/finish",
        headers=headers,
        json={
            "tasks_completed": 0,
            "last_task_text": "Focusing",
            "end_reason": "app_closed",
        },
    )
    assert empty_resp.status_code == 200
    assert empty_resp.json()["duration_minutes"] == 0
    assert empty_resp.json()["earned_leaves"] == 0
    assert empty_resp.json()["end_reason"] == "app_closed"


def test_text_length_caps_enforced_across_endpoints(client: TestClient):
    user = create_user_helper(client, "caps@cozy.com")
    headers = user["headers"]

    # Report reason > 200 chars -> 422
    long_reason_resp = client.post(
        "/api/safety/report",
        headers=headers,
        json={"target_id": "other_user", "reason": "R" * 201},
    )
    assert long_reason_resp.status_code == 422

    # Friend player_tag > 16 chars -> 422
    long_tag_resp = client.post(
        "/api/friends/request",
        headers=headers,
        json={"player_tag": "T" * 17},
    )
    assert long_tag_resp.status_code == 422

    # Finish session last_task_text > 120 chars cleanly clamped to 120 chars
    sid = client.post("/api/sessions/start", headers=headers).json()["session_id"]
    finish_resp = client.post(
        f"/api/sessions/{sid}/finish",
        headers=headers,
        json={"tasks_completed": 1, "last_task_text": "  " + ("X" * 150) + "  "},
    )
    assert finish_resp.status_code == 200
    assert finish_resp.json()["last_task_text"] == "X" * 120
