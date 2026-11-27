"""Import the 1.1.0 Supabase data into the 2.0 FastAPI database ([AUTH-12]).

Reads directly from the Supabase Postgres database and writes to DATABASE_URL, so
user data never lands in a dump file. Run it from ``backend/`` on the staging or
production host, never on a development PC:

    SUPABASE_DB_URL='postgresql://postgres.<ref>:<password>@<pooler-host>:5432/postgres'
    DATABASE_URL='postgresql://...'               # the 2.0 database
    python -m scripts.import_supabase --check     # schema check only, reads no user rows
    python -m scripts.import_supabase --dry-run   # full import, rolled back
    python -m scripts.import_supabase             # full import, committed

What is kept:
- user IDs (so RevenueCat app user IDs and friend links stay valid),
- emails, bcrypt password hashes of confirmed email accounts, Google ``sub`` IDs,
- profiles (display name, player tag, leaves, focus minutes, supporter flag, equipped theme),
- friendships, blocks, reports, owned catalog items and processed focus sessions.

What is dropped: deleted and anonymous users, unprocessed sessions, FCM tokens
(the 2.0 app registers a fresh one), items that are not in the 2.0 catalog.

Re-running is safe: rows are upserted by primary key. Rows removed from Supabase
between runs are not deleted from the target, so the release-day import should
go into an empty production database.

Output contains counts and opaque user IDs only, never emails or names.
"""
from __future__ import annotations

import argparse
import datetime
import json
import os
import re
import sys
import uuid
from collections import Counter
from dataclasses import dataclass, field
from typing import Any, Iterable, Optional

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.engine import Engine
from sqlalchemy.orm import Session

from app.database import normalize_database_url
from app.models.friendship import Friendship
from app.models.profile import Profile
from app.models.safety import UserBlock, UserReport
from app.models.session import FocusSession
from app.models.shop import Item, UserInventory
from app.models.user import User
from app.services.tag_generator import PLAYER_TAG_CHARSET, TAG_LENGTH

EMAIL_PATTERN = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
LEGACY_TAG_PATTERN = re.compile(r"^[A-Z0-9]{4,8}$")
DISPLAY_NAME_MAX = 30
TASK_TEXT_MAX = 120
REPORT_REASON_MAX = 200
DEFAULT_DISPLAY_NAME = "Cozy Doubler"
DEFAULT_THEME_ID = "system_default"
IMPORTED_END_REASON = "imported_v1"
REPORT_ID_NAMESPACE = uuid.UUID("6f1d9c62-6c43-4c4e-9a4e-8f0a2f1d3c11")

# Columns the importer needs from each Supabase table (schema, table): required, optional.
SOURCE_TABLES: dict[tuple[str, str], tuple[set[str], set[str]]] = {
    ("auth", "users"): (
        {"id", "email", "encrypted_password", "created_at"},
        {"deleted_at", "email_confirmed_at", "is_anonymous"},
    ),
    ("auth", "identities"): (
        {"user_id", "provider", "identity_data"},
        {"provider_id"},
    ),
    ("public", "profiles"): (
        {"id", "display_name", "player_tag", "leaves", "total_minutes_focused", "is_supporter"},
        {"equipped_theme_id", "created_at"},
    ),
    ("public", "friendships"): (
        {"user_id_1", "user_id_2", "requester_id", "status"},
        {"accepted_at", "created_at"},
    ),
    ("public", "focus_sessions"): (
        {"id", "user_id", "start_time", "end_time", "tasks_completed", "earned_leaves",
         "is_processed", "duration_minutes"},
        {"last_task_text"},
    ),
    ("public", "user_inventory"): ({"user_id", "item_id"}, {"purchased_at", "acquired_at", "created_at"}),
    ("public", "user_blocks"): ({"blocker_id", "blocked_id"}, {"created_at"}),
    ("public", "items"): ({"id"}, set()),
}
# report_user() wrote to one of these; the first one that exists is used.
REPORT_TABLE_CANDIDATES = ("user_reports", "reports")
REPORT_COLUMNS = ({"reported_id", "reason"}, {"id", "reporter_id", "created_at"})


class SchemaMismatch(Exception):
    pass


@dataclass
class SourceSnapshot:
    users: list[dict] = field(default_factory=list)
    identities: list[dict] = field(default_factory=list)
    profiles: list[dict] = field(default_factory=list)
    friendships: list[dict] = field(default_factory=list)
    focus_sessions: list[dict] = field(default_factory=list)
    inventory: list[dict] = field(default_factory=list)
    blocks: list[dict] = field(default_factory=list)
    reports: list[dict] = field(default_factory=list)
    item_ids: set[str] = field(default_factory=set)
    # False when auth.users has no email_confirmed_at column (password hashes are then kept).
    has_email_confirmation: bool = True


@dataclass
class ImportReport:
    counts: Counter = field(default_factory=Counter)
    warnings: list[str] = field(default_factory=list)

    def add(self, key: str, n: int = 1) -> None:
        self.counts[key] += n

    def warn(self, message: str) -> None:
        self.warnings.append(message)

    def render(self) -> str:
        lines = ["Import summary:"]
        for key in sorted(self.counts):
            lines.append(f"  {key}: {self.counts[key]}")
        if self.warnings:
            lines.append("Warnings:")
            lines.extend(f"  - {w}" for w in self.warnings)
        return "\n".join(lines)


# --------------------------------------------------------------------------- reading

def check_source_schema(engine: Engine) -> tuple[dict[tuple[str, str], set[str]], Optional[str]]:
    """Returns the columns available per table and the report table name.

    Raises SchemaMismatch listing every missing table or required column.
    Reads only the catalog, no user rows.
    """
    inspector = inspect(engine)
    available: dict[tuple[str, str], set[str]] = {}
    problems: list[str] = []
    for (schema, table), (required, optional) in SOURCE_TABLES.items():
        if not inspector.has_table(table, schema=schema):
            problems.append(f"missing table {schema}.{table}")
            continue
        cols = {c["name"] for c in inspector.get_columns(table, schema=schema)}
        missing = required - cols
        if missing:
            problems.append(f"{schema}.{table} lacks column(s): {', '.join(sorted(missing))}")
        available[(schema, table)] = cols & (required | optional)

    report_table = None
    for candidate in REPORT_TABLE_CANDIDATES:
        if inspector.has_table(candidate, schema="public"):
            cols = {c["name"] for c in inspector.get_columns(candidate, schema="public")}
            required, optional = REPORT_COLUMNS
            if required <= cols:
                report_table = candidate
                available[("public", candidate)] = cols & (required | optional)
                break
    if problems:
        raise SchemaMismatch("; ".join(problems))
    return available, report_table


def _select(conn, schema: str, table: str, columns: Iterable[str]) -> list[dict]:
    cols = sorted(columns)
    sql = f'SELECT {", ".join(cols)} FROM {schema}.{table}'
    return [dict(row) for row in conn.execute(text(sql)).mappings()]


def read_snapshot(engine: Engine) -> SourceSnapshot:
    available, report_table = check_source_schema(engine)
    snap = SourceSnapshot()
    snap.has_email_confirmation = "email_confirmed_at" in available[("auth", "users")]
    with engine.connect() as conn:
        snap.users = _select(conn, "auth", "users", available[("auth", "users")])
        snap.identities = _select(conn, "auth", "identities", available[("auth", "identities")])
        snap.profiles = _select(conn, "public", "profiles", available[("public", "profiles")])
        snap.friendships = _select(conn, "public", "friendships", available[("public", "friendships")])
        snap.focus_sessions = _select(conn, "public", "focus_sessions", available[("public", "focus_sessions")])
        snap.inventory = _select(conn, "public", "user_inventory", available[("public", "user_inventory")])
        snap.blocks = _select(conn, "public", "user_blocks", available[("public", "user_blocks")])
        snap.item_ids = {str(r["id"]) for r in _select(conn, "public", "items", {"id"})}
        if report_table:
            snap.reports = _select(conn, "public", report_table, available[("public", report_table)])
    return snap


# --------------------------------------------------------------------------- helpers

def _id(value: Any) -> Optional[str]:
    return None if value is None else str(value)


def _dt(value: Any) -> Optional[datetime.datetime]:
    """Naive UTC datetime, matching the 2.0 schema (datetime.utcnow defaults)."""
    if value is None or value == "":
        return None
    if isinstance(value, str):
        value = datetime.datetime.fromisoformat(value.replace("Z", "+00:00"))
    if isinstance(value, datetime.datetime) and value.tzinfo is not None:
        value = value.astimezone(datetime.timezone.utc).replace(tzinfo=None)
    return value


def _int(value: Any) -> int:
    try:
        return max(0, int(value or 0))
    except (TypeError, ValueError):
        return 0


def _bool(value: Any) -> bool:
    if isinstance(value, str):
        return value.strip().lower() in {"1", "t", "true", "yes"}
    return bool(value)


def _json(value: Any) -> dict:
    if isinstance(value, dict):
        return value
    if isinstance(value, (str, bytes)) and value:
        try:
            parsed = json.loads(value)
            return parsed if isinstance(parsed, dict) else {}
        except ValueError:
            return {}
    return {}


def _google_subs(identities: list[dict]) -> dict[str, str]:
    subs: dict[str, str] = {}
    for ident in identities:
        if (ident.get("provider") or "").lower() != "google":
            continue
        data = _json(ident.get("identity_data"))
        sub = ident.get("provider_id") or data.get("sub") or data.get("provider_id")
        if sub:
            subs[str(ident["user_id"])] = str(sub)
    return subs


def _tag_available(db: Session, tag: str, user_id: str, used: set[str]) -> bool:
    if not tag or not LEGACY_TAG_PATTERN.match(tag) or tag in used:
        return False
    owner = db.query(Profile.id).filter(Profile.player_tag == tag).first()
    return owner is None or owner[0] == user_id


def _new_tag(db: Session, used: set[str]) -> str:
    import secrets
    while True:
        candidate = "".join(secrets.choice(PLAYER_TAG_CHARSET) for _ in range(TAG_LENGTH))
        if candidate in used:
            continue
        if db.query(Profile.id).filter(Profile.player_tag == candidate).first() is None:
            return candidate


# --------------------------------------------------------------------------- importing

def import_snapshot(snap: SourceSnapshot, db: Session) -> ImportReport:
    """Upserts the snapshot into ``db`` (not committed). The 2.0 catalog must be seeded."""
    report = ImportReport()
    catalog = {row[0] for row in db.query(Item.id).all()}
    unknown_items = sorted(snap.item_ids - catalog)
    if unknown_items:
        report.warn(f"Supabase items missing from the 2.0 catalog (ownership dropped): {', '.join(unknown_items)}")

    imported = _import_users(snap, db, report)
    db.flush()
    _import_profiles(snap, db, report, imported, catalog)
    db.flush()
    blocked_pairs = _import_blocks(snap, db, report, imported)
    _import_inventory(snap, db, report, imported, catalog)
    _import_friendships(snap, db, report, imported, blocked_pairs)
    _import_sessions(snap, db, report, imported)
    _import_reports(snap, db, report, imported)
    db.flush()
    return report


def _import_users(snap: SourceSnapshot, db: Session, report: ImportReport) -> set[str]:
    google_subs = _google_subs(snap.identities)
    seen_emails: set[str] = set()
    seen_subs: set[str] = set()
    imported: set[str] = set()

    for row in sorted(snap.users, key=lambda r: (_dt(r.get("created_at")) or datetime.datetime.min, str(r["id"]))):
        user_id = str(row["id"])
        if row.get("deleted_at"):
            report.add("users.skipped.deleted")
            continue
        if _bool(row.get("is_anonymous")):
            report.add("users.skipped.anonymous")
            continue
        email = (row.get("email") or "").strip().lower()
        if not email or not EMAIL_PATTERN.match(email):
            report.add("users.skipped.no_valid_email")
            continue
        if email in seen_emails:
            report.add("users.skipped.duplicate_email")
            report.warn(f"user {user_id}: email already used by an earlier Supabase account; skipped")
            continue

        google_sub = google_subs.get(user_id)
        if google_sub and google_sub in seen_subs:
            report.warn(f"user {user_id}: Google account already linked to another user; link dropped")
            google_sub = None

        hashed = row.get("encrypted_password") or None
        if hashed and not str(hashed).startswith("$2"):
            report.add("users.password_dropped.not_bcrypt")
            hashed = None
        if hashed and snap.has_email_confirmation and not row.get("email_confirmed_at"):
            # An unconfirmed email/password account may not belong to the email's owner; keeping
            # its password would let it receive the owner's later Google sign-in (verified link).
            report.add("users.password_dropped.unconfirmed_email")
            hashed = None

        conflict = db.query(User).filter(User.email == email, User.id != user_id).first()
        if conflict is not None:
            report.add("users.skipped.target_email_conflict")
            report.warn(f"user {user_id}: email already belongs to target user {conflict.id}; skipped")
            continue
        if google_sub:
            conflict = db.query(User).filter(User.google_sub == google_sub, User.id != user_id).first()
            if conflict is not None:
                report.add("users.skipped.target_google_conflict")
                report.warn(f"user {user_id}: Google account already linked to target user {conflict.id}; skipped")
                continue

        user = db.get(User, user_id)
        if user is None:
            user = User(id=user_id)
            db.add(user)
            report.add("users.inserted")
        else:
            report.add("users.updated")
        user.email = email
        user.hashed_password = hashed
        user.google_sub = google_sub
        user.created_at = _dt(row.get("created_at")) or datetime.datetime.utcnow()

        if not hashed and not google_sub:
            report.add("users.without_sign_in_method")
            report.warn(f"user {user_id}: no Google link and no usable password; needs password reset to sign in")

        seen_emails.add(email)
        if google_sub:
            seen_subs.add(google_sub)
        imported.add(user_id)
    return imported


def _import_profiles(snap: SourceSnapshot, db: Session, report: ImportReport, imported: set[str], catalog: set[str]) -> None:
    by_id = {str(p["id"]): p for p in snap.profiles}
    used_tags: set[str] = set()
    for user_id in sorted(imported):
        row = by_id.get(user_id)
        if row is None:
            report.add("profiles.created_default")
            row = {}

        profile = db.get(Profile, user_id)
        tag = (row.get("player_tag") or "").strip().upper().lstrip("#")
        if not _tag_available(db, tag, user_id, used_tags):
            previous = profile.player_tag if profile is not None else ""
            if _tag_available(db, previous, user_id, used_tags):
                tag = previous  # keep the tag a previous import run generated
            else:
                if row:
                    report.add("profiles.player_tag_regenerated")
                    report.warn(f"user {user_id}: player tag invalid or taken; a new one was generated")
                tag = _new_tag(db, used_tags)
        used_tags.add(tag)

        theme = _id(row.get("equipped_theme_id"))
        if theme is not None and theme not in catalog:
            report.add("profiles.equipped_theme_reset")
            theme = DEFAULT_THEME_ID
        if theme is None:
            theme = DEFAULT_THEME_ID

        if profile is None:
            profile = Profile(id=user_id)
            db.add(profile)
            report.add("profiles.inserted")
        else:
            report.add("profiles.updated")
        profile.display_name = (row.get("display_name") or "").strip()[:DISPLAY_NAME_MAX] or DEFAULT_DISPLAY_NAME
        profile.player_tag = tag
        profile.equipped_theme_id = theme
        profile.leaves = _int(row.get("leaves"))
        profile.total_minutes_focused = _int(row.get("total_minutes_focused"))
        profile.is_supporter = _bool(row.get("is_supporter"))
        profile.fcm_token = None
        profile.created_at = _dt(row.get("created_at")) or db.get(User, user_id).created_at

        # Every 2.0 account owns the default theme.
        db.merge(UserInventory(user_id=user_id, item_id=DEFAULT_THEME_ID,
                               acquired_at=profile.created_at))


def _import_blocks(snap: SourceSnapshot, db: Session, report: ImportReport, imported: set[str]) -> set[frozenset]:
    pairs: set[frozenset] = set()
    for row in snap.blocks:
        blocker, blocked = _id(row["blocker_id"]), _id(row["blocked_id"])
        if blocker not in imported or blocked not in imported or blocker == blocked:
            report.add("blocks.skipped")
            continue
        db.merge(UserBlock(blocker_id=blocker, blocked_id=blocked,
                           created_at=_dt(row.get("created_at")) or datetime.datetime.utcnow()))
        pairs.add(frozenset((blocker, blocked)))
        report.add("blocks.imported")
    return pairs


def _import_inventory(snap: SourceSnapshot, db: Session, report: ImportReport, imported: set[str], catalog: set[str]) -> None:
    for row in snap.inventory:
        user_id, item_id = _id(row["user_id"]), _id(row["item_id"])
        if user_id not in imported:
            report.add("inventory.skipped.user")
            continue
        if item_id not in catalog:
            report.add("inventory.skipped.unknown_item")
            continue
        acquired = (_dt(row.get("purchased_at") or row.get("acquired_at") or row.get("created_at"))
                    or datetime.datetime.utcnow())
        db.merge(UserInventory(user_id=user_id, item_id=item_id, acquired_at=acquired))
        report.add("inventory.imported")


def _import_friendships(snap: SourceSnapshot, db: Session, report: ImportReport, imported: set[str], blocked_pairs: set[frozenset]) -> None:
    best: dict[tuple[str, str], dict] = {}
    for row in snap.friendships:
        a, b = _id(row["user_id_1"]), _id(row["user_id_2"])
        requester = _id(row["requester_id"])
        status = (row.get("status") or "").strip().lower()
        if status not in {"pending", "accepted"}:
            report.add("friendships.skipped.status")
            continue
        if a not in imported or b not in imported or a == b or requester not in (a, b):
            report.add("friendships.skipped.users")
            continue
        if frozenset((a, b)) in blocked_pairs:
            report.add("friendships.skipped.blocked")
            continue
        key = (min(a, b), max(a, b))
        previous = best.get(key)
        if previous is None or (previous["status"] != "accepted" and status == "accepted"):
            best[key] = {"requester": requester, "status": status,
                         "created_at": _dt(row.get("created_at")),
                         "accepted_at": _dt(row.get("accepted_at"))}

    for (low, high), data in sorted(best.items()):
        accepted_at = data["accepted_at"]
        if data["status"] == "accepted" and accepted_at is None:
            accepted_at = data["created_at"] or datetime.datetime.utcnow()
        db.merge(Friendship(
            user_id_1=low, user_id_2=high, requester_id=data["requester"], status=data["status"],
            created_at=data["created_at"] or accepted_at or datetime.datetime.utcnow(),
            accepted_at=accepted_at if data["status"] == "accepted" else None,
        ))
        report.add(f"friendships.imported.{data['status']}")


def _import_sessions(snap: SourceSnapshot, db: Session, report: ImportReport, imported: set[str]) -> None:
    for row in snap.focus_sessions:
        user_id = _id(row["user_id"])
        end_time = _dt(row.get("end_time"))
        start_time = _dt(row.get("start_time"))
        if user_id not in imported:
            report.add("sessions.skipped.user")
            continue
        if not _bool(row.get("is_processed")) or end_time is None or start_time is None:
            report.add("sessions.skipped.unfinished")
            continue
        duration = _int(row.get("duration_minutes"))
        task_text = (row.get("last_task_text") or "").strip()[:TASK_TEXT_MAX] or None
        db.merge(FocusSession(
            id=str(row["id"]), user_id=user_id, start_time=start_time, end_time=end_time,
            tasks_completed=_int(row.get("tasks_completed")), earned_leaves=_int(row.get("earned_leaves")),
            is_processed=True, duration_minutes=duration, last_task_text=task_text,
            active_seconds=duration * 60, ever_connected=True,
            last_seen_at=end_time, last_activity_at=end_time, end_reason=IMPORTED_END_REASON,
        ))
        report.add("sessions.imported")


def _import_reports(snap: SourceSnapshot, db: Session, report: ImportReport, imported: set[str]) -> None:
    for row in snap.reports:
        reported = _id(row["reported_id"])
        reporter = _id(row.get("reporter_id"))
        if reported not in imported:
            report.add("reports.skipped.user")
            continue
        if reporter not in imported:
            reporter = None
        reason = (row.get("reason") or "").strip()[:REPORT_REASON_MAX] or "unspecified"
        created_at = _dt(row.get("created_at")) or datetime.datetime.utcnow()
        report_id = _id(row.get("id")) or str(uuid.uuid5(
            REPORT_ID_NAMESPACE, f"{row.get('reporter_id')}|{reported}|{reason}|{row.get('created_at')}"))
        db.merge(UserReport(id=report_id, reporter_id=reporter, reported_id=reported,
                            reason=reason, created_at=created_at))
        report.add("reports.imported")


# --------------------------------------------------------------------------- CLI

def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Import 1.1.0 Supabase data into the 2.0 database.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="only verify the Supabase schema (reads no user rows)")
    mode.add_argument("--dry-run", action="store_true", help="run the full import, then roll back")
    args = parser.parse_args(argv)

    source_url = os.getenv("SUPABASE_DB_URL")
    if not source_url:
        print("SUPABASE_DB_URL is not set (use an environment variable, not a command-line argument).", file=sys.stderr)
        return 2
    source = create_engine(normalize_database_url(source_url))

    try:
        available, report_table = check_source_schema(source)
    except SchemaMismatch as exc:
        print(f"Supabase schema does not match what the importer expects: {exc}", file=sys.stderr)
        return 2
    print("Supabase schema OK." + ("" if report_table else " (No reports table found; reports are skipped.)"))
    if args.check:
        return 0

    # Imported here so --check works without a configured target database.
    from app.database import Base, SessionLocal, engine, ensure_schema_migrations
    from app.seed import sync_catalog

    Base.metadata.create_all(bind=engine)
    ensure_schema_migrations(engine)
    with SessionLocal() as db:
        sync_catalog(db)
        db.commit()

    snapshot = read_snapshot(source)
    with SessionLocal() as db:
        report = import_snapshot(snapshot, db)
        if args.dry_run:
            db.rollback()
            print("Dry run: nothing was written.")
        else:
            db.commit()
            print("Import committed.")
    print(report.render())
    return 0


if __name__ == "__main__":
    sys.exit(main())
