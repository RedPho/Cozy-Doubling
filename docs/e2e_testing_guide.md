# Cozy Doubling — End-to-End (E2E) Test Automation Guide

This document describes the architecture, tooling, clean-architecture guarantees, execution commands, and maintenance standards for the Cozy Doubling **Android + FastAPI Backend End-to-End (E2E) Test Automation Suite**.

---

## 1. Framework Selection & Rationale

The E2E suite uses **Jetpack Compose UI Test (`androidx.compose.ui.test.junit4`) + AndroidJUnit4 + Espresso** running as Android instrumentation tests (`android/app/src/androidTest/java/com/grepho/cozydoubling/e2e/`) against a **real, isolated FastAPI + Uvicorn backend process**.

### Why Jetpack Compose UI Test + AndroidJUnit4?
1. **Native First-Class Support:** Already wired into `android/app/build.gradle.kts` (`ui-test-junit4`, `ui-test-manifest`, `espresso-core`, `AndroidJUnit4`) with zero external Node/Appium/Maestro daemon dependencies.
2. **Full Real Stack Coverage:** Launches the real `MainActivity` composable hierarchy, Navigation (`AppNavHost`), ViewModels, `ServiceLocator` (`DefaultAppContainer`), `Remote*Repository` implementations, OkHttp REST + WebSocket clients, and `LocalTaskDataSource` against the real FastAPI routers, SQLAlchemy ORM, SQLite database, and live WebSocket managers (`room_manager` & `safety_manager`).
3. **Deterministic Synchronization:** Compose's idling clock plus bounded `waitUntil` assertions eliminate brittle `Thread.sleep` waits while exercising live network & WebSocket latency.
4. **Reliable Multi-User Testing on One Machine:** Two Android Emulators running simultaneously on one Windows/macOS host share `AndroidWifi` (`10.0.2.16`) and can suffer host-loopback socket contention. Instead, the E2E suite drives **User A** through the real Android UI on the emulator while a test-only `BackendPeerClient` inside `androidTest` acts as **User B** against the **exact same live backend** (`BuildConfig.API_BASE_URL`) over real HTTP and WebSockets. Every multi-user room, friend, and safety flow executes real server logic, database writes, and WebSocket broadcasts end-to-end.

---

## 2. Clean Architecture Preservation

The E2E automation strictly upholds Cozy Doubling's clean architecture boundaries (verified automatically by `ArchitectureBoundaryTest`):

- **Production UI & Domain Layers Stay Uncoupled:**
  - Feature packages (`features/**`), `core/**` (except `core/di`), and `ui/**` **never** import `data.*`, `okhttp3.*`, or wire-protocol annotations (`@SerialName`).
  - Only purely semantic UI accessibility/test tags (`Modifier.testTag(...)` and descriptive `contentDescription`) are used in UI composables — zero backend URLs, zero test-mode flags, and zero mock branches exist in production screens or ViewModels.
- **Zero Production Backend Backdoors:**
  - The FastAPI backend runs its normal production ASGI app (`app.main:app`) with no test-only endpoints.
  - Test isolation is achieved exclusively via an ephemeral SQLite database (`DATABASE_URL=sqlite:///.../e2e_test.db`) created and cleaned up by the runner script.
- **Test-Only Helpers Live Exclusively in `androidTest`:**
  - `E2eTestHarness` and `BackendPeerClient` reside solely in `android/app/src/androidTest/java/com/grepho/cozydoubling/e2e/` and are never packaged into the release or debug app APK.

---

## 3. Architecture Overview

```
┌────────────────────────────────────────────────────────────────────┐
│  scripts/run_e2e.py (Single-Command Orchestrator)                  │
│  1. Creates temp SQLite DB & starts live Uvicorn (127.0.0.1:8000)  │
│  2. Waits for GET /health == 200                                   │
│  3. Detects or boots Android Emulator AVD                          │
│  4. Runs `adb reverse tcp:8000 tcp:8000` & disables animations     │
│  5. Grants POST_NOTIFICATIONS runtime permission                   │
│  6. Runs `gradlew connectedDebugAndroidTest                        │
│           -PapiBaseUrl=http://127.0.0.1:8000`                      │
│  7. Tears down Uvicorn & cleans up temp DB                         │
└───────────────────────────────┬────────────────────────────────────┘
                                │
        ┌───────────────────────┴───────────────────────┐
        ▼                                               ▼
┌──────────────────────────────┐        ┌──────────────────────────────┐
│ Android Emulator             │        │ Live FastAPI / Uvicorn       │
│  - Real MainActivity / UI    │◄─HTTP─►│  - /api/auth, /api/profile   │
│  - Real ViewModels           │◄──WS──►│  - /api/sessions, /api/shop  │
│  - Real Remote*Repositories  │        │  - /api/friends, /api/safety │
│  - E2E Compose Test Cases    │        │  - /ws/room, /ws/safety      │
│  - Test-Only BackendPeerClient (User B)│  - Ephemeral SQLite DB      │
└──────────────────────────────┘        └──────────────────────────────┘
```

---

## 4. Test Suite Structure & Ticket Traceability

All E2E test tickets and acceptance criteria are tracked in [specs/02_e2e_test_tickets.md](../specs/02_e2e_test_tickets.md).

| Test Class (`androidTest/.../e2e/`) | Tickets Covered | Feature Scope |
|---|---|---|
| `E2eTestHarness.kt` | `E2E-01` | Isolated container reset, local task cleanup, Compose wait helpers, live `BackendPeerClient` |
| `AuthAndSettingsE2eTest.kt` | `E2E-02`, `E2E-09` | Email auto-register/login, email normalization, wrong password rejection, Google sign-in, display name edit + blank rejection, sign-out cleanup, permanent account deletion |
| `FocusRoomAndSummaryE2eTest.kt` | `E2E-03`, `E2E-04`, `E2E-05` | Task CRUD, blank task guard, active task switching, completed task delete guard, unfinished task persistence, real-time multi-user presence & task sync, session finish reward formula, minimum 1-leaf floor, Summary → Home → Journey updates |
| `FriendsAndSafetyE2eTest.kt` | `E2E-06`, `E2E-09` | Connect code display, friend request happy path (case/`#` normalization), invalid/self/duplicate errors, incoming request accept & reject, mutual auto-accept, friend session story vs no-session fallback, report user, real-time block removal from Focus Room and Friends |
| `ShopInventoryJourneyE2eTest.kt` | `E2E-07`, `E2E-08`, `E2E-05` | Seeded catalog browsing, insufficient leaves edge case, theme purchase with leaves & balance deduction, Supporter Pass purchase & Restore Purchases, Inventory equip/unequip & persistence across navigation, Journey lifetime stats |

---

## 5. How to Run

### Prerequisites
- Python 3.10+ with `backend/requirements.txt` installed
- Android SDK (`adb`, `emulator`) and at least one AVD installed (or a physical Android device with USB debugging enabled)
- JDK 17+ (e.g., Android Studio bundled JBR)

### One-Command Full E2E Run (Recommended)
From the repository root:

```bash
python scripts/run_e2e.py
```

Optional flags:
- `--port 8000` — Port for the ephemeral local backend (default: `8000`)
- `--avd <AVD_NAME>` — Specific AVD to launch if no device/emulator is already attached
- `--test-class com.grepho.cozydoubling.e2e.AuthAndSettingsE2eTest` — Run a single E2E test class
- `--keep-emulator` — Leave a newly booted emulator running after the test run finishes

### Running Backend & Android Unit / Architecture Tests
```bash
# Backend API + WebSocket live tests
python backend/run_tests.py

# Android unit tests + Clean Architecture boundary verification
cd android
./gradlew testDebugUnitTest
```

---

## 6. Test Isolation & Anti-Flake Guidelines

1. **Per-Test State Reset:** Every `@Test` starts by calling `E2eTestHarness.resetAppState()` before launching `MainActivity`, which resets `ServiceLocator.useDefaultContainer()` (fresh `ApiClient` & repositories) and clears `LocalTaskDataSource`.
2. **Unique Accounts per Test:** Each test generates unique per-test emails (using a short random suffix) so tests never interfere with each other even when sharing one ephemeral run database.
3. **Bounded Eventual Assertions:** All network/WebSocket-backed UI transitions use `waitUntilNodeExists` / `waitUntilNodeDoesNotExist` with explicit timeouts rather than fixed sleeps.
4. **Animations Disabled:** `scripts/run_e2e.py` sets Android animation scales to `0` during execution so Compose transitions settle deterministically.
5. **Backend Logs Go to a File:** The runner writes Uvicorn output to a temp log file (never an undrained pipe, which stalls the server on Windows) and prints the last 60 lines when a run fails.
6. **API 36+/37 Emulators Need Espresso ≥ 3.7.0:** Older Espresso versions crash with `NoSuchMethodException: InputManager.getInstance` on Android 16/17 images.
7. **Wait for Snackbars to Dismiss** before tapping FABs underneath them, and wait for list headers (e.g. `Your Neighbourhood (1)`) to settle before clicking items inside a `LazyColumn` that just re-laid out.
