"""[AUTH-12] Supabase -> FastAPI importer, against a synthetic Supabase-shaped database.

The source is SQLite with two attached in-memory databases named ``auth`` and ``public``,
so the importer's ``schema.table`` queries and catalog checks run unchanged.
All data here is made up.
"""
import bcrypt
import pytest
from sqlalchemy import create_engine, event, text
from sqlalchemy.pool import StaticPool

from app.database import get_db, normalize_database_url
from app.main import app
from app.models.friendship import Friendship
from app.models.profile import Profile
from app.models.safety import UserBlock, UserReport
from app.models.session import FocusSession
from app.models.shop import UserInventory
from app.models.user import User
from app.services.auth import verify_password
from scripts.import_supabase import (
    SchemaMismatch,
    check_source_schema,
    import_snapshot,
    read_snapshot,
)

GOOGLE_ID = "00000000-0000-4000-8000-00000000000a"
EMAIL_ID = "00000000-0000-4000-8000-00000000000b"
UNCONFIRMED_ID = "00000000-0000-4000-8000-00000000000c"
DELETED_ID = "00000000-0000-4000-8000-00000000000d"
ANON_ID = "00000000-0000-4000-8000-00000000000e"
NO_PROFILE_ID = "00000000-0000-4000-8000-00000000000f"

SOURCE_DDL = [
    """CREATE TABLE auth.users (id TEXT PRIMARY KEY, email TEXT, encrypted_password TEXT,
        email_confirmed_at TEXT, created_at TEXT, deleted_at TEXT, is_anonymous BOOLEAN)""",
    """CREATE TABLE auth.identities (id TEXT, user_id TEXT, provider TEXT, provider_id TEXT,
        identity_data TEXT)""",
    """CREATE TABLE public.profiles (id TEXT PRIMARY KEY, display_name TEXT, player_tag TEXT,
        equipped_theme_id TEXT, leaves INTEGER, created_at TEXT, total_minutes_focused INTEGER,
        is_supporter BOOLEAN, fcm_token TEXT)""",
    """CREATE TABLE public.friendships (user_id_1 TEXT, user_id_2 TEXT, requester_id TEXT,
        status TEXT, created_at TEXT, accepted_at TEXT)""",
    """CREATE TABLE public.focus_sessions (id TEXT PRIMARY KEY, user_id TEXT, start_time TEXT,
        end_time TEXT, tasks_completed INTEGER, earned_leaves INTEGER, is_processed BOOLEAN,
        duration_minutes INTEGER, last_task_text TEXT)""",
    "CREATE TABLE public.user_inventory (user_id TEXT, item_id TEXT, purchased_at TEXT)",  # 1.1.0 name
    "CREATE TABLE public.user_blocks (blocker_id TEXT, blocked_id TEXT, created_at TEXT)",
    "CREATE TABLE public.items (id TEXT PRIMARY KEY, leaf_price INTEGER)",
    """CREATE TABLE public.reports (id TEXT PRIMARY KEY, reporter_id TEXT, reported_id TEXT,
        reason TEXT, created_at TEXT)""",
]


def _hash(password: str) -> str:
    # Supabase (GoTrue) stores $2a$ bcrypt hashes.
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=4, prefix=b"2a")).decode()


def _source_engine(skip_tables: tuple[str, ...] = ()):
    engine = create_engine("sqlite://", poolclass=StaticPool)

    @event.listens_for(engine, "connect")
    def _attach(dbapi_conn, _record):
        dbapi_conn.execute("ATTACH DATABASE ':memory:' AS auth")
        dbapi_conn.execute("ATTACH DATABASE ':memory:' AS public")

    with engine.begin() as conn:
        for ddl in SOURCE_DDL:
            if not any(f".{t} " in ddl for t in skip_tables):
                conn.execute(text(ddl))
    return engine


def _seed_source(engine):
    password_hash = _hash("password123")
    users = [
        (GOOGLE_ID, "Gale@Example.com", "", "2025-01-01T10:00:00Z", "2025-01-01T10:00:00Z", None, False),
        (EMAIL_ID, "emma@example.com", password_hash, "2025-01-02T10:00:00Z", "2025-01-02T10:00:00+00:00", None, False),
        (UNCONFIRMED_ID, "una@example.com", password_hash, None, "2025-01-03T10:00:00Z", None, False),
        (DELETED_ID, "dee@example.com", password_hash, "2025-01-04T10:00:00Z", "2025-01-04T10:00:00Z", "2025-02-01T00:00:00Z", False),
        (ANON_ID, None, "", None, "2025-01-05T10:00:00Z", None, True),
        (NO_PROFILE_ID, "nora@example.com", password_hash, "2025-01-06T10:00:00Z", "2025-01-06T10:00:00Z", None, False),
    ]
    with engine.begin() as conn:
        conn.execute(text("INSERT INTO auth.users VALUES (:id,:e,:p,:c,:ca,:d,:a)"),
                     [dict(id=u[0], e=u[1], p=u[2], c=u[3], ca=u[4], d=u[5], a=u[6]) for u in users])
        conn.execute(text("INSERT INTO auth.identities VALUES (:id,:u,:p,:pid,:data)"), [
            dict(id="i1", u=GOOGLE_ID, p="google", pid="google-sub-gale", data='{"sub": "google-sub-gale"}'),
            dict(id="i2", u=EMAIL_ID, p="email", pid=EMAIL_ID, data="{}"),
        ])
        conn.execute(text("INSERT INTO public.profiles VALUES (:id,:n,:t,:th,:l,:ca,:m,:s,:f)"), [
            dict(id=GOOGLE_ID, n="  Gale  ", t="abcd2345", th="theme_midnight_velvet", l=150,
                 ca="2025-01-01T10:00:00Z", m=300, s=True, f="old-fcm-token"),
            # Same tag as Gale (case differs): must be regenerated, not merged.
            dict(id=EMAIL_ID, n="Emma", t="ABCD2345", th="theme_removed", l=40,
                 ca="2025-01-02T10:00:00Z", m=60, s=False, f=None),
            dict(id=UNCONFIRMED_ID, n="Una", t="UNAUNA22", th=None, l=0,
                 ca="2025-01-03T10:00:00Z", m=0, s=False, f=None),
            dict(id=DELETED_ID, n="Dee", t="DEEDEE22", th=None, l=999,
                 ca="2025-01-04T10:00:00Z", m=0, s=False, f=None),
        ])
        conn.execute(text("INSERT INTO public.friendships VALUES (:a,:b,:r,:s,:c,:acc)"), [
            # Stored with the larger ID first: must be canonicalised.
            dict(a=EMAIL_ID, b=GOOGLE_ID, r=GOOGLE_ID, s="accepted", c="2025-01-10T00:00:00Z", acc="2025-01-11T00:00:00Z"),
            dict(a=GOOGLE_ID, b=DELETED_ID, r=GOOGLE_ID, s="accepted", c="2025-01-10T00:00:00Z", acc=None),
            dict(a=GOOGLE_ID, b=NO_PROFILE_ID, r=NO_PROFILE_ID, s="rejected", c="2025-01-10T00:00:00Z", acc=None),
            dict(a=EMAIL_ID, b=UNCONFIRMED_ID, r=UNCONFIRMED_ID, s="pending", c="2025-01-10T00:00:00Z", acc=None),
        ])
        conn.execute(text("INSERT INTO public.user_blocks VALUES (:a,:b,:c)"), [
            dict(a=EMAIL_ID, b=UNCONFIRMED_ID, c="2025-01-12T00:00:00Z"),
            dict(a=GOOGLE_ID, b=DELETED_ID, c="2025-01-12T00:00:00Z"),
        ])
        conn.execute(text("INSERT INTO public.focus_sessions VALUES (:id,:u,:s,:e,:t,:l,:p,:d,:txt)"), [
            dict(id="11111111-0000-4000-8000-000000000001", u=GOOGLE_ID, s="2025-01-20T10:00:00Z",
                 e="2025-01-20T10:50:00Z", t=3, l=25, p=True, d=50, txt="Write chapter one"),
            dict(id="11111111-0000-4000-8000-000000000002", u=GOOGLE_ID, s="2025-01-21T10:00:00Z",
                 e=None, t=0, l=0, p=False, d=0, txt=None),
            dict(id="11111111-0000-4000-8000-000000000003", u=DELETED_ID, s="2025-01-21T10:00:00Z",
                 e="2025-01-21T11:00:00Z", t=1, l=5, p=True, d=60, txt=None),
        ])
        conn.execute(text("INSERT INTO public.user_inventory VALUES (:u,:i,:a)"), [
            dict(u=GOOGLE_ID, i="theme_peach_blossom", a="2025-01-15T00:00:00Z"),
            dict(u=GOOGLE_ID, i="theme_removed", a="2025-01-15T00:00:00Z"),
            dict(u=DELETED_ID, i="theme_peach_blossom", a="2025-01-15T00:00:00Z"),
        ])
        conn.execute(text("INSERT INTO public.items VALUES (:id, 0)"), [
            dict(id=i) for i in ("system_default", "theme_peach_blossom", "theme_midnight_velvet", "theme_removed")
        ])
        conn.execute(text("INSERT INTO public.reports VALUES (:id,:r,:t,:why,:c)"), [
            dict(id="22222222-0000-4000-8000-000000000001", r=EMAIL_ID, t=UNCONFIRMED_ID,
                 why="spam in task text", c="2025-01-13T00:00:00Z"),
            dict(id="22222222-0000-4000-8000-000000000002", r=DELETED_ID, t=GOOGLE_ID,
                 why="x" * 500, c="2025-01-13T00:00:00Z"),
        ])


@pytest.fixture
def source():
    engine = _source_engine()
    _seed_source(engine)
    return engine


@pytest.fixture
def target_db(client):
    # Use the app's DB override so API calls in the same test see the imported rows.
    gen = app.dependency_overrides[get_db]()
    db = next(gen)
    yield db
    gen.close()


def _run(source_engine, db):
    report = import_snapshot(read_snapshot(source_engine), db)
    db.commit()
    return report


def test_imports_accounts_profiles_and_relations(source, target_db):
    report = _run(source, target_db)
    db = target_db

    assert report.counts["users.inserted"] == 4
    assert report.counts["users.skipped.deleted"] == 1
    assert report.counts["users.skipped.anonymous"] == 1
    assert report.counts["users.password_dropped.unconfirmed_email"] == 1
    assert {u.id for u in db.query(User).all()} == {GOOGLE_ID, EMAIL_ID, UNCONFIRMED_ID, NO_PROFILE_ID}

    gale = db.get(User, GOOGLE_ID)
    assert gale.email == "gale@example.com"
    assert gale.google_sub == "google-sub-gale"
    assert gale.hashed_password is None

    emma = db.get(User, EMAIL_ID)
    assert verify_password("password123", emma.hashed_password)
    assert db.get(User, UNCONFIRMED_ID).hashed_password is None

    gale_profile = db.get(Profile, GOOGLE_ID)
    assert (gale_profile.display_name, gale_profile.player_tag) == ("Gale", "ABCD2345")
    assert (gale_profile.leaves, gale_profile.total_minutes_focused, gale_profile.is_supporter) == (150, 300, True)
    assert gale_profile.equipped_theme_id == "theme_midnight_velvet"
    assert gale_profile.fcm_token is None

    emma_profile = db.get(Profile, EMAIL_ID)
    assert emma_profile.player_tag != "ABCD2345" and len(emma_profile.player_tag) == 8
    assert emma_profile.equipped_theme_id == "system_default"
    assert report.counts["profiles.player_tag_regenerated"] == 1

    nora_profile = db.get(Profile, NO_PROFILE_ID)
    assert nora_profile.display_name == "Cozy Doubler" and len(nora_profile.player_tag) == 8

    owned = {(i.user_id, i.item_id) for i in db.query(UserInventory).all()}
    assert (GOOGLE_ID, "theme_peach_blossom") in owned
    bought = db.get(UserInventory, (GOOGLE_ID, "theme_peach_blossom")).acquired_at
    assert bought.date().isoformat() == "2025-01-15"  # 1.1.0 purchased_at is kept
    assert (GOOGLE_ID, "theme_removed") not in owned
    assert all((uid, "system_default") in owned for uid in (GOOGLE_ID, EMAIL_ID, UNCONFIRMED_ID, NO_PROFILE_ID))
    assert any("theme_removed" in w for w in report.warnings)

    friendships = db.query(Friendship).all()
    assert len(friendships) == 1
    f = friendships[0]
    assert (f.user_id_1, f.user_id_2, f.status, f.requester_id) == (GOOGLE_ID, EMAIL_ID, "accepted", GOOGLE_ID)
    assert report.counts["friendships.skipped.blocked"] == 1
    assert report.counts["friendships.skipped.status"] == 1

    assert {(b.blocker_id, b.blocked_id) for b in db.query(UserBlock).all()} == {(EMAIL_ID, UNCONFIRMED_ID)}

    sessions = db.query(FocusSession).all()
    assert [s.id for s in sessions] == ["11111111-0000-4000-8000-000000000001"]
    assert sessions[0].is_processed and sessions[0].earned_leaves == 25
    assert sessions[0].last_task_text == "Write chapter one"
    assert sessions[0].end_reason == "imported_v1"

    reports = {r.id: r for r in db.query(UserReport).all()}
    assert reports["22222222-0000-4000-8000-000000000001"].reporter_id == EMAIL_ID
    assert reports["22222222-0000-4000-8000-000000000002"].reporter_id is None
    assert len(reports["22222222-0000-4000-8000-000000000002"].reason) == 200

    rendered = report.render()
    assert "example.com" not in rendered and "Gale" not in rendered


def test_imported_users_can_sign_in(source, target_db, client):
    _run(source, target_db)

    resp = client.post("/api/auth/login", json={"email": "Emma@example.com", "password": "password123"})
    assert resp.status_code == 200, resp.text
    me = client.get("/api/auth/me", headers={"Authorization": f"Bearer {resp.json()['access_token']}"}).json()
    assert me["id"] == EMAIL_ID and me["leaves"] == 40

    # The Google account is matched on its sub, keeping the 1.1.0 user ID and progress.
    resp = client.post("/api/auth/google", json={"id_token": "mock:google:google-sub-gale:Gale:gale@example.com"})
    assert resp.status_code == 200, resp.text
    me = client.get("/api/auth/me", headers={"Authorization": f"Bearer {resp.json()['access_token']}"}).json()
    assert me["id"] == GOOGLE_ID and me["leaves"] == 150 and me["player_tag"] == "ABCD2345"

    # Unconfirmed email accounts lose their password; it must not still work.
    resp = client.post("/api/auth/login", json={"email": "una@example.com", "password": "password123"})
    assert resp.status_code == 401


def test_reimport_is_idempotent(source, target_db):
    _run(source, target_db)
    tags = {p.id: p.player_tag for p in target_db.query(Profile).all()}

    report = _run(source, target_db)

    assert report.counts["users.updated"] == 4 and report.counts["users.inserted"] == 0
    assert target_db.query(User).count() == 4
    assert target_db.query(Friendship).count() == 1
    assert target_db.query(FocusSession).count() == 1
    assert target_db.query(UserReport).count() == 2
    assert {p.id: p.player_tag for p in target_db.query(Profile).all()} == tags


def test_skips_users_whose_email_already_exists_in_target(source, target_db):
    target_db.add(User(id="someone-else", email="emma@example.com", hashed_password=None))
    target_db.commit()

    report = _run(source, target_db)

    assert report.counts["users.skipped.target_email_conflict"] == 1
    assert target_db.get(User, EMAIL_ID) is None
    assert target_db.get(User, "someone-else").email == "emma@example.com"


def test_schema_check_names_missing_tables_and_columns():
    engine = _source_engine(skip_tables=("identities",))
    with engine.begin() as conn:
        conn.execute(text("DROP TABLE public.profiles"))
        conn.execute(text("CREATE TABLE public.profiles (id TEXT PRIMARY KEY, display_name TEXT)"))

    with pytest.raises(SchemaMismatch) as exc:
        check_source_schema(engine)
    message = str(exc.value)
    assert "auth.identities" in message
    assert "public.profiles" in message and "player_tag" in message


def test_normalize_database_url():
    assert normalize_database_url("postgres://u:p@h:5432/db") == "postgresql+psycopg://u:p@h:5432/db"
    assert normalize_database_url("postgresql://u:p@h/db") == "postgresql+psycopg://u:p@h/db"
    assert normalize_database_url("postgresql+psycopg://u@h/db") == "postgresql+psycopg://u@h/db"
