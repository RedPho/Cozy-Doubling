import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent

_DEV_SECRET_KEY = "cozy-doubling-super-secret-key-change-in-production-2026"
_DEV_WEBHOOK_AUTH = "cozy_rc_secret_key"


def _csv_env(name: str) -> list[str]:
    return [part.strip() for part in os.getenv(name, "").split(",") if part.strip()]


class Settings:
    # "development" (default, local + E2E) or "production".
    # Production disables every developer shortcut (mock Google tokens, client-asserted
    # supporter status) and refuses to boot with the built-in development secrets.
    ENVIRONMENT: str = os.getenv("ENVIRONMENT", "development").strip().lower()

    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        f"sqlite:///{(BASE_DIR / 'cozy_doubling.db').resolve().as_posix()}"
    )
    SECRET_KEY: str = os.getenv("SECRET_KEY", _DEV_SECRET_KEY)
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 30  # 30 days
    REVENUECAT_WEBHOOK_AUTH: str = os.getenv("REVENUECAT_WEBHOOK_AUTH", _DEV_WEBHOOK_AUTH)
    # Secret RevenueCat REST API key (sk_...) used to verify entitlements server-side on "Restore".
    REVENUECAT_API_KEY: str = os.getenv("REVENUECAT_API_KEY", "")
    ROOM_CAPACITY_LIMIT: int = int(os.getenv("ROOM_CAPACITY_LIMIT", "50"))
    SUPPORTER_ENTITLEMENT_ID: str = "Cozy Doubling Supporter Pass"
    MAX_SESSION_DURATION_MINUTES: int = int(os.getenv("MAX_SESSION_DURATION_MINUTES", "720"))
    DISCONNECT_GRACE_MINUTES: int = int(os.getenv("DISCONNECT_GRACE_MINUTES", "10"))
    ABANDON_TIMEOUT_MINUTES: int = int(os.getenv("ABANDON_TIMEOUT_MINUTES", "30"))
    # Upper bound of completed tasks that earn leaves in a single session (anti leaf-farming).
    MAX_REWARDED_TASKS_PER_SESSION: int = int(os.getenv("MAX_REWARDED_TASKS_PER_SESSION", "20"))

    # OAuth client IDs whose Google ID tokens are accepted (the Android app's *web* client ID).
    GOOGLE_CLIENT_IDS: list[str] = _csv_env("GOOGLE_CLIENT_IDS")
    # Path to a Firebase service-account JSON. When unset, push notifications are only logged.
    FIREBASE_CREDENTIALS_FILE: str = os.getenv("FIREBASE_CREDENTIALS_FILE", "")

    MIN_PASSWORD_LENGTH: int = 8

    @property
    def is_production(self) -> bool:
        return self.ENVIRONMENT == "production"

    @property
    def allow_mock_google_tokens(self) -> bool:
        return not self.is_production

    @property
    def allow_client_supporter_sync(self) -> bool:
        """Dev/E2E only: lets the fake billing client assert supporter status directly."""
        return not self.is_production

    def validate(self) -> list[str]:
        """Returns configuration problems that must block a production boot."""
        problems: list[str] = []
        if not self.is_production:
            return problems
        if self.SECRET_KEY == _DEV_SECRET_KEY or len(self.SECRET_KEY) < 32:
            problems.append("SECRET_KEY must be set to a random value of at least 32 characters.")
        if self.REVENUECAT_WEBHOOK_AUTH == _DEV_WEBHOOK_AUTH:
            problems.append("REVENUECAT_WEBHOOK_AUTH must be set to the secret configured in RevenueCat.")
        if not self.GOOGLE_CLIENT_IDS:
            problems.append("GOOGLE_CLIENT_IDS must list the Google OAuth web client ID(s).")
        if self.DATABASE_URL.startswith("sqlite"):
            problems.append("DATABASE_URL must point to a managed database (not SQLite) in production.")
        return problems


settings = Settings()
