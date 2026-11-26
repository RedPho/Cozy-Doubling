from typing import Generator
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import declarative_base, sessionmaker, Session
from app.config import settings

def normalize_database_url(url: str) -> str:
    """Hosts (and Supabase) hand out postgres:// URLs; SQLAlchemy needs the psycopg 3 driver named."""
    for prefix in ("postgres://", "postgresql://"):
        if url.startswith(prefix):
            return "postgresql+psycopg://" + url[len(prefix):]
    return url

DATABASE_URL = normalize_database_url(settings.DATABASE_URL)
connect_args = {"check_same_thread": False} if "sqlite" in DATABASE_URL else {}

engine = create_engine(
    DATABASE_URL,
    echo=False,
    connect_args=connect_args
)

SessionLocal = sessionmaker(
    autocommit=False,
    autoflush=False,
    bind=engine
)

Base = declarative_base()

def ensure_schema_migrations(target_engine=engine) -> None:
    """Idempotently add new columns to existing SQLite/SQL tables."""
    inspector = inspect(target_engine)
    if inspector.has_table("users"):
        user_cols = {col["name"] for col in inspector.get_columns("users")}
        if "google_sub" not in user_cols:
            with target_engine.begin() as conn:
                conn.execute(text("ALTER TABLE users ADD COLUMN google_sub VARCHAR"))
                conn.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_google_sub ON users (google_sub)"))

    if not inspector.has_table("focus_sessions"):
        return

    existing_cols = {col["name"] for col in inspector.get_columns("focus_sessions")}
    migrations = [
        ("active_seconds", "INTEGER NOT NULL DEFAULT 0"),
        ("ever_connected", "BOOLEAN NOT NULL DEFAULT 0"),
        ("last_connected_at", "DATETIME"),
        ("last_disconnected_at", "DATETIME"),
        ("last_seen_at", "DATETIME"),
        ("last_activity_at", "DATETIME"),
        ("end_reason", "VARCHAR"),
        ("was_capped", "BOOLEAN NOT NULL DEFAULT 0"),
    ]
    with target_engine.begin() as conn:
        for col_name, col_type in migrations:
            if col_name not in existing_cols:
                conn.execute(text(f"ALTER TABLE focus_sessions ADD COLUMN {col_name} {col_type}"))

def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
