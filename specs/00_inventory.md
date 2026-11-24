# Cozy Doubling — System Architecture & Technical Inventory

> **Purpose:** Living architectural inventory of the Cozy Doubling native Android client, independent FastAPI backend, database schema, WebSocket channels, and automated test pyramid.

---

## 1. Repository & Clean Architecture Overview

Cozy Doubling is structured as two decoupled codebases in one monorepo:

- **`android/`** — Native Android app (Kotlin, Jetpack Compose, Material 3, Coroutines/Flow).
- **`backend/`** — Independent Python FastAPI service (SQLAlchemy ORM, SQLite/PostgreSQL, JWT/Bcrypt auth, native WebSockets).
- **`scripts/`** — Automation scripts (including `run_e2e.py` for 1-device and real 2-device Android + Backend E2E execution).
- **`specs/`** — Product epics (`epics.md`), prioritized product backlog (`01_product_backlog.md`), E2E test tickets (`02_e2e_test_tickets.md`), and this technical inventory (`00_inventory.md`).

### Clean Architecture Boundary (Enforced Automatically)
`ArchitectureBoundaryTest.kt` enforces strict separation on every Android unit test run:
- `features/**`, `core/**` (except `core/di/`), and `ui/**` **must never** import `com.grepho.cozydoubling.data.*`, `okhttp3.*`, or wire-protocol annotations (`@SerialName`).
- All HTTP/WebSocket transport and wire DTO mapping (`RemoteModels.kt`, `ApiClient.kt`, `Remote*Repository.kt`) live exclusively in `android/app/src/main/java/com/grepho/cozydoubling/data/remote/`.
- All ViewModels depend only on domain repository interfaces injected via `ServiceLocator` (`DefaultAppContainer` in production, `FakeAppContainer` in unit tests).

---

## 2. Android Client Module Breakdown

| Domain | Screens / Components | ViewModel / Manager | Domain Repository Interface | Remote Implementation |
|---|---|---|---|---|
| **Auth** | `LoginPage.kt`, `AppNavHost.kt` | `LoginViewModel` | `core/auth/AuthRepository` | `RemoteAuthRepository` |
| **Profile** | `HomePage.kt`, `CozyTopBar.kt` | `HomeViewModel` | `core/profile/ProfileRepository` | `RemoteProfileRepository` |
| **Focus Room** | `FocusRoomPage.kt`, `LocalTaskDataSource.kt` | `FocusRoomViewModel` | `features/room/FocusRoomRepository` | `RemoteFocusRoomRepository` |
| **Summary** | `SummaryPage.kt` | `SummaryViewModel` | `FocusRoomRepository` | `RemoteFocusRoomRepository` |
| **Friends** | `FriendsPage.kt` (Oasis tab) | `FriendsViewModel` | `features/friends/FriendsRepository` | `RemoteFriendsRepository` |
| **Shop & Economy** | `ShopPage.kt` (Oasis tab) | `ShopViewModel` | `core/economy/EconomyRepository` | `RemoteEconomyRepository` |
| **Inventory & Theming** | `InventoryPage.kt`, `CozyDoublingTheme` | `InventoryViewModel` | `EconomyRepository`, `ProfileRepository` | `RemoteEconomyRepository` |
| **Billing / Subscriptions** | `ShopPage.kt`, `SettingsPage.kt` | `ShopViewModel`, `SettingsViewModel` | `core/billing/BillingRepository` | `RemoteBillingRepository` |
| **Journey** | `JourneyPage.kt` (Oasis tab) | `JourneyViewModel` | `ProfileRepository` | `RemoteProfileRepository` |
| **Safety** | `FocusRoomPage.kt`, `FriendsPage.kt` | `FocusRoomViewModel`, `FriendsViewModel` | `core/safety/SafetyRepository` | `RemoteSafetyRepository` |
| **Notifications** | `CozyFirebaseMessagingService.kt` | `MainActivity`, `SettingsViewModel` | `core/notifications/NotificationRepository` | `RemoteNotificationRepository` |
| **Connectivity** | `ConnectionErrorDialog.kt` | `ConnectionStateManager`, `ConnectivityObserver` | *(Local Android `ConnectivityManager`)* | — |

---

## 3. FastAPI Backend API & WebSocket Surface

### 3.1 REST Endpoints

| Router (`backend/app/routers/`) | Endpoints | Responsibility |
|---|---|---|
| `auth.py` (`/api/auth`) | `POST /register`, `POST /login`, `POST /google`, `GET /me`, `POST /logout`, `DELETE /account` | JWT issuance, Bcrypt hashing, profile & 8-char tag provisioning, 7-step ordered cascade account deletion |
| `profile.py` (`/api/profile`) | `GET ""`, `PATCH ""`, `POST /fcm-token` | Profile read, whitelisted display-name update (non-blank validated), push token registration |
| `sessions.py` (`/api/sessions`) | `POST /start`, `POST /{session_id}/finish`, `GET /{session_id}` | Server-timestamped session creation, friend push notification dispatch, single-processing reward calculation (`max(1, duration_min * 1 + tasks * 3)`) |
| `friends.py` (`/api/friends`) | `POST /request`, `POST /{sender_id}/accept`, `POST /{sender_id}/reject`, `DELETE /{friend_id}`, `GET ""`, `GET /requests` | Tag lookup (case/`#` normalized), canonical `(user_id_1 < user_id_2)` uniqueness, mutual auto-accept, unfriend, block-filtered friend stories & incoming requests |
| `shop.py` (`/api/shop`) | `GET /items`, `POST /buy` | Seeded catalog with Material 3 palettes (`System Default`, `Peach Blossom`, `Midnight Velvet`, supporter passes), atomic leaf purchase |
| `inventory.py` (`/api/inventory`) | `GET ""`, `POST /equip`, `POST /unequip` | Owned + supporter-unlocked themes, ownership-validated equipping, revert to `system_default` |
| `subscriptions.py` (`/api/subscriptions`) | `POST /webhook`, `POST /sync` | Entitlement activation (`INITIAL_PURCHASE`/`RENEWAL`/`RESTORE`/`TRANSFER`) & expiration (`EXPIRATION`/`REVOCATION`) with automatic premium-theme reversion |
| `safety.py` (`/api/safety`) | `POST /block`, `POST /unblock`, `GET /blocks`, `POST /report` | Idempotent blocking/unblocking, real-time WebSocket notifications to both users, room separation, conduct violation reporting |

### 3.2 Real-Time WebSockets (`backend/app/main.py` & `websockets/`)

| Endpoint | Manager | Protocol Events |
|---|---|---|
| `WS /ws/room?token=&presence_key=&name=` | `FocusRoomManager` | `presence_state`, `presence_update`, `presence_leave`, `ping`/`pong`; enforces `ROOM_CAPACITY_LIMIT`, 25s keepalive, bidirectional block filtering, and immediate removal on block |
| `WS /ws/safety?token=` | `SafetyManager` | `block_added`, `block_removed`, `ping`/`pong`; pushes live block updates to both blocker and blocked user |

### 3.3 Core Database Tables (`backend/app/models/`)

| Table | Key Columns & Constraints |
|---|---|
| `users` | `id` (PK), `email` (unique, normalized lowercase), `hashed_password` (nullable for Google) |
| `profiles` | `id` (PK/FK `users.id`), `display_name`, `player_tag` (unique 8-char charset `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`), `equipped_theme_id`, `leaves`, `total_minutes_focused`, `is_supporter`, `fcm_token`, `created_at` |
| `focus_sessions` | `id` (PK), `user_id`, `start_time`, `end_time`, `duration_minutes`, `tasks_completed`, `earned_leaves`, `last_task_text`, `is_processed` |
| `friendships` | Composite PK `(user_id_1, user_id_2)` with canonical `user_id_1 < user_id_2`, `requester_id`, `status` (`pending`/`accepted`), `accepted_at` |
| `item_categories`, `items`, `theme_details`, `user_inventory` | Seeded catalog + 18-token Material 3 palette + per-user owned items |
| `user_blocks`, `user_reports` | Bidirectional block filtering + conduct violation audit trail |

---

## 4. Automated Verification Pyramid

| Layer | Command | Coverage |
|---|---|---|
| **Backend API & Live WebSocket** | `python backend/run_tests.py` | 25 tests covering auth, profile, sessions, friends, shop, inventory, subscriptions, safety, and live Uvicorn WebSocket concurrency/pool checks |
| **Android Unit & Architecture** | `cd android && ./gradlew testDebugUnitTest` | ViewModel logic, repository integration, and `ArchitectureBoundaryTest` clean-architecture guard |
| **Full-Stack 1- & 2-Device E2E** | `python scripts/run_e2e.py` | 11 suites/scenarios (4 single-device + 7 real 2-emulator concurrent pairs) against live FastAPI backend ([docs/e2e_testing_guide.md](../docs/e2e_testing_guide.md)) |

---

## 5. Known Gaps Tracked in Backlog
- **Unfriend UI (`[SOC-05]`):** Backend `DELETE /api/friends/{friend_id}` exists; Android `FriendsPage` / `FriendsRepository` UI wiring needed.
- **Unblock UI (`[SAFE-04]`):** Backend `POST /api/safety/unblock` exists; Settings "Blocked Users" management screen needed.
- **Background Focus Continuity (`[ROOM-06]`):** Android foreground service, persistent notification, and optional floating mini-widget needed so backgrounding does not drop sessions or WebSockets.
- **Persistent Auth Token Storage (`[AUTH-09]`):** `ApiClient.authToken` is currently in-memory only; encrypted local token persistence is needed to survive cold app restarts.
