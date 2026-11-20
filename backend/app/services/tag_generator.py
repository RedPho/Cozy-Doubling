import secrets
from sqlalchemy.orm import Session
from app.models.profile import Profile

PLAYER_TAG_CHARSET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
TAG_LENGTH = 8

def generate_player_tag(db: Session) -> str:
    """
    Generates a unique 8-character player tag from the allowed charset.
    Loops with collision retry until an unused tag is found.
    """
    while True:
        candidate = "".join(secrets.choice(PLAYER_TAG_CHARSET) for _ in range(TAG_LENGTH))
        if db.query(Profile.id).filter(Profile.player_tag == candidate).first() is None:
            return candidate
