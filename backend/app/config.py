import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent

class Settings:
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        f"sqlite:///{(BASE_DIR / 'cozy_doubling.db').resolve().as_posix()}"
    )
    SECRET_KEY: str = os.getenv("SECRET_KEY", "cozy-doubling-super-secret-key-change-in-production-2026")
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 30  # 30 days
    REVENUECAT_WEBHOOK_AUTH: str = os.getenv("REVENUECAT_WEBHOOK_AUTH", "cozy_rc_secret_key")
    ROOM_CAPACITY_LIMIT: int = int(os.getenv("ROOM_CAPACITY_LIMIT", "50"))
    SUPPORTER_ENTITLEMENT_ID: str = "Cozy Doubling Supporter Pass"

settings = Settings()
