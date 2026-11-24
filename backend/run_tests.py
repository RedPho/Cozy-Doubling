import sys
import os

os.environ["TESTING"] = "1"
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

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

def override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()

app.dependency_overrides[get_db] = override_get_db

def reset_db():
    Base.metadata.drop_all(bind=test_engine)
    Base.metadata.create_all(bind=test_engine)
    with TestingSessionLocal() as session:
        seed_catalog_if_empty(session)

reset_db()
client = TestClient(app, raise_server_exceptions=True)

print("=== Running Backend Tests ===", flush=True)

from tests.test_auth import (
    test_register_and_login_success,
    test_duplicate_registration_fails,
    test_invalid_login_credentials,
    test_google_sign_in_mock,
    test_logout_and_account_deletion,
)
reset_db()
test_register_and_login_success(client)
print("[PASS] test_register_and_login_success", flush=True)

reset_db()
test_duplicate_registration_fails(client)
print("[PASS] test_duplicate_registration_fails", flush=True)

reset_db()
test_invalid_login_credentials(client)
print("[PASS] test_invalid_login_credentials", flush=True)

reset_db()
test_google_sign_in_mock(client)
print("[PASS] test_google_sign_in_mock", flush=True)

reset_db()
test_logout_and_account_deletion(client)
print("[PASS] test_logout_and_account_deletion", flush=True)

from tests.test_profile import (
    test_profile_read_and_update_name,
    test_profile_fcm_token_update,
)
reset_db()
test_profile_read_and_update_name(client)
print("[PASS] test_profile_read_and_update_name", flush=True)

reset_db()
test_profile_fcm_token_update(client)
print("[PASS] test_profile_fcm_token_update", flush=True)

from tests.test_sessions import (
    test_session_lifecycle_and_leaf_rewards,
    test_session_minimum_reward_floor,
)
reset_db()
test_session_lifecycle_and_leaf_rewards(client)
print("[PASS] test_session_lifecycle_and_leaf_rewards", flush=True)

reset_db()
test_session_minimum_reward_floor(client)
print("[PASS] test_session_minimum_reward_floor", flush=True)

from tests.test_friends import (
    test_friend_request_flow,
    test_mutual_auto_acceptance,
)
reset_db()
test_friend_request_flow(client)
print("[PASS] test_friend_request_flow", flush=True)

reset_db()
test_mutual_auto_acceptance(client)
print("[PASS] test_mutual_auto_acceptance", flush=True)

from tests.test_shop_inventory import test_shop_items_listing_and_leaf_purchase
reset_db()
test_shop_items_listing_and_leaf_purchase(client)
print("[PASS] test_shop_items_listing_and_leaf_purchase", flush=True)

from tests.test_safety import test_blocking_and_reporting_flow
reset_db()
test_blocking_and_reporting_flow(client)
print("[PASS] test_blocking_and_reporting_flow", flush=True)

from tests.test_subscriptions import test_revenuecat_webhook_and_auto_revert
reset_db()
test_revenuecat_webhook_and_auto_revert(client)
print("[PASS] test_revenuecat_webhook_and_auto_revert", flush=True)

from tests.test_websockets import (
    test_two_clients_full_presence_lifecycle,
    test_three_concurrent_clients_presence,
    test_safety_blocking_filters_presence_bidirectionally,
    test_realtime_block_separates_room_participants,
    test_reconnect_replaces_old_socket_cleanly,
    test_room_capacity_limit_rejection,
    test_invalid_token_rejected_on_connect,
    test_safety_websocket_realtime_events,
    test_two_clients_same_account_distinct_sessions,
)
reset_db()
test_two_clients_full_presence_lifecycle(client)
print("[PASS] test_two_clients_full_presence_lifecycle", flush=True)

reset_db()
test_three_concurrent_clients_presence(client)
print("[PASS] test_three_concurrent_clients_presence", flush=True)

reset_db()
test_safety_blocking_filters_presence_bidirectionally(client)
print("[PASS] test_safety_blocking_filters_presence_bidirectionally", flush=True)

reset_db()
test_realtime_block_separates_room_participants(client)
print("[PASS] test_realtime_block_separates_room_participants", flush=True)

reset_db()
test_reconnect_replaces_old_socket_cleanly(client)
print("[PASS] test_reconnect_replaces_old_socket_cleanly", flush=True)

reset_db()
test_room_capacity_limit_rejection(client)
print("[PASS] test_room_capacity_limit_rejection", flush=True)

reset_db()
test_invalid_token_rejected_on_connect(client)
print("[PASS] test_invalid_token_rejected_on_connect", flush=True)

reset_db()
test_safety_websocket_realtime_events(client)
print("[PASS] test_safety_websocket_realtime_events", flush=True)

reset_db()
test_two_clients_same_account_distinct_sessions(client)
print("[PASS] test_two_clients_same_account_distinct_sessions", flush=True)

from tests.test_websockets_live import (
    test_live_uvicorn_two_clients_bidirectional_presence_and_keepalive,
    test_live_uvicorn_many_websockets_do_not_exhaust_db_pool,
)
reset_db()
test_live_uvicorn_two_clients_bidirectional_presence_and_keepalive(client)
print("[PASS] test_live_uvicorn_two_clients_bidirectional_presence_and_keepalive", flush=True)

reset_db()
test_live_uvicorn_many_websockets_do_not_exhaust_db_pool(client)
print("[PASS] test_live_uvicorn_many_websockets_do_not_exhaust_db_pool", flush=True)

print("=== ALL 25 BACKEND TESTS PASSED SUCCESSFULLY! ===", flush=True)
