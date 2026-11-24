import pytest
from starlette.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import Base, get_db
from app.main import app
from app.seed import seed_catalog_if_empty

test_engine = create_engine(
    "sqlite:///:memory:",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=test_engine)

@pytest.fixture(scope="function", autouse=True)
def setup_db():
    Base.metadata.create_all(bind=test_engine)
    with TestingSessionLocal() as session:
        seed_catalog_if_empty(session)
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
