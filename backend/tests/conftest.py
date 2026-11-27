import pytest
from starlette.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import Base, get_db
from app.main import app
from app.seed import sync_catalog

test_engine = create_engine(
    "sqlite:///:memory:",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=test_engine)

@pytest.fixture(scope="function", autouse=True)
def setup_db():
    from app.services.login_throttle import login_throttle
    login_throttle.clear()
    Base.metadata.create_all(bind=test_engine)
    with TestingSessionLocal() as session:
        sync_catalog(session)
    yield
    Base.metadata.drop_all(bind=test_engine)

@pytest.fixture(scope="function")
def client():
    def override_get_db():
        db = TestingSessionLocal()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()

def create_user_helper(client: TestClient, email: str, password: str = "password123", name: str = "Cozy Tester") -> dict:
    resp = client.post("/api/auth/register", json={
        "email": email,
        "password": password,
        "display_name": name
    })
    assert resp.status_code == 200, f"Registration failed: {resp.text}"
    token = resp.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}
    me_resp = client.get("/api/auth/me", headers=headers)
    assert me_resp.status_code == 200, f"Get me failed: {me_resp.text}"
    profile = me_resp.json()
    return {"token": token, "headers": headers, "profile": profile}


def seeded_token(user_id: str, display_name: str, email: str | None = None) -> str:
    """Creates a user + profile row directly and returns a valid access token for it.

    WebSocket endpoints resolve the participant's display name from the profile, so tokens for
    users that do not exist in the database are rejected.
    """
    from app.models.user import User
    from app.models.profile import Profile
    from app.services.auth import create_access_token

    # Use the app's active DB override: pytest may import this conftest twice (as "conftest" and
    # "tests.conftest"), each with its own in-memory engine.
    db_dep = app.dependency_overrides.get(get_db, get_db)
    gen = db_dep()
    db = next(gen)
    try:
        if not db.query(User).filter(User.id == user_id).first():
            db.add(User(id=user_id, email=email or f"{user_id}@cozy.test", hashed_password=None))
            db.flush()
            db.add(Profile(id=user_id, display_name=display_name, player_tag=user_id.upper()[:8].ljust(8, "X")))
            db.commit()
    finally:
        gen.close()
    return create_access_token({"sub": user_id, "email": email or f"{user_id}@cozy.test"})
