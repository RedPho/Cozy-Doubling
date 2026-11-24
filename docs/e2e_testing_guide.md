# Cozy Doubling — End-to-End (E2E) Test Automation Guide

This document describes the architecture, tooling, clean-architecture guarantees, multi-emulator networking setup, execution commands, and maintenance standards for the Cozy Doubling **Android + FastAPI Backend End-to-End (E2E) Test Automation Suite**.

---

## 1. Framework Selection & Rationale

The E2E suite uses **Jetpack Compose UI Test (`androidx.compose.ui.test.junit4`) + AndroidJUnit4 + Espresso** running as Android instrumentation tests (`android/app/src/androidTest/java/com/grepho/cozydoubling/e2e/`) across **1, 2, and 3 real Android devices/emulators** against a **real, isolated FastAPI + Uvicorn backend process**.

### Why Jetpack Compose UI Test + Multi-Emulator Execution?
1. **Native First-Class Support:** Already wired into `android/app/build.gradle.kts` (`ui-test-junit4`, `ui-test-manifest`, `espresso-core`, `AndroidJUnit4`) with zero external Node/Appium/Maestro daemon dependencies.
2. **Full Real Stack Coverage on Every User Device:** Every participant in single-user, 2-user, and 3-user scenarios runs the real `MainActivity` composable hierarchy, Navigation (`AppNavHost`), ViewModels, `ServiceLocator` (`DefaultAppContainer`), `Remote*Repository` implementations, OkHttp REST + WebSocket clients, and `LocalTaskDataSource` against the real FastAPI routers, SQLAlchemy ORM, SQLite database, and live WebSocket managers (`room_manager` & `safety_manager`).
3. **Real Multi-Emulator Networking Without `AndroidWifi` Collision:** On one host PC, two or more Android Emulators with `AndroidWifi` enabled all share the virtual `10.0.2.16` interface and collide on host-loopback WebSocket connections. To eliminate this cleanly:
   - **Device 1 (`emulator-5554`, `Medium_Phone_API_37.0`)**: keeps Wi-Fi enabled.
   - **Device 2 (`emulator-5556`, `Medium_Phone_API_37.0_2`)**: runner disables Wi-Fi (`svc wifi disable`) and enables cellular data (`svc data enable`).
   - **Device 3 (`emulator-5558`, `Medium_Phone_API_37.0 -read-only`)**: runner disables Wi-Fi (`svc wifi disable`) and enables cellular data (`svc data enable`).
   - All emulators route both backend (`tcp:<port>`) and coordinator (`tcp:<coord-port>`) traffic over USB/ADB via `adb -s <serial> reverse`.
4. **Deterministic Synchronization:** `scripts/run_e2e.py` hosts a test-only `ThreadingHTTPServer` (`E2eCoordinator`) offering `/kv` key-value exchange and `/barrier` rendezvous primitives so independent Android devices synchronize cleanly at runtime without any test-only endpoints on the production backend.

---

## 2. Clean Architecture Preservation

The E2E automation strictly upholds Cozy Doubling's clean architecture boundaries (verified automatically by `ArchitectureBoundaryTest`):

- **Production UI & Domain Layers Stay Uncoupled:**
  - Feature packages (`features/**`), `core/**` (except `core/di`), and `ui/**` **never** import `data.*`, `okhttp3.*`, or wire-protocol annotations (`@SerialName`).
  - Only purely semantic UI accessibility/test tags (`Modifier.testTag(...)` and descriptive `contentDescription`) are used in UI composables — zero backend URLs, zero test-mode flags, and zero mock branches exist in production screens or ViewModels.
- **Zero Production Backend Backdoors:**
  - The FastAPI backend runs its normal production ASGI app (`app.main:app`) with no test-only endpoints.
  - Test isolation is achieved via an ephemeral SQLite database (`DATABASE_URL=sqlite:///.../cozy_e2e.db`) created and cleaned up by `scripts/run_e2e.py`.
- **Test-Only Helpers Live Exclusively in `androidTest`:**
  - `E2eTestHarness`, `E2eCoordinator`, `AbortCoordinatorOnFailureRule`, and `BackendTestHelper` reside solely in `android/app/src/androidTest/java/com/grepho/cozydoubling/e2e/` and are never packaged into the app APK.

---

## 3. Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────────┐
│  scripts/run_e2e.py (Single-Command Multi-Emulator Orchestrator)           │
│  1. Creates temp SQLite DB & starts live Uvicorn (127.0.0.1:8010)          │
│  2. Starts test-only E2eCoordinator HTTP server (127.0.0.1:8011)           │
│  3. Detects or boots 1 / 2 / 3 Android Emulators depending on --mode       │
│  4. On Emulators 2 & 3: runs `svc wifi disable` + `svc data enable`        │
│  5. Configures `adb reverse` for backend & coordinator on all emulators    │
│  6. Builds debug & androidTest APKs ONCE and installs across all devices   │
│  7. Runs single-device, 2-device concurrent pairs, & 3-device triples      │
│  8. Tears down emulators (unless --keep-emulators), backend, & temp DB     │
└───────────┬──────────────────────────┬──────────────────────────┬──────────┘
            │                          │                          │
            ▼                          ▼                          ▼
┌──────────────────────┐   ┌──────────────────────┐   ┌──────────────────────┐
│ Emulator 1 (Wi-Fi)   │   │ Emulator 2 (No Wi-Fi)│   │ Emulator 3 (No Wi-Fi)│
│ MainActivity + UI    │   │ MainActivity + UI    │   │ MainActivity + UI    │
└───────────┬──────────┘   └───────────┬──────────┘   └───────────┬──────────┘
            └──────────────────────────┼──────────────────────────┘
                                  HTTP + WebSockets
                                       ▼
                        ┌──────────────────────────────┐
                        │ Live FastAPI / Uvicorn       │
                        │  - /api/* & /ws/room,safety  │
                        │  - Ephemeral SQLite DB       │
                        └──────────────────────────────┘
```

---

## 4. Test Suite Structure & Ticket Traceability

All E2E test tickets and acceptance criteria are tracked in [specs/02_e2e_test_tickets.md](../specs/02_e2e_test_tickets.md).

| Package / Class (`androidTest/.../e2e/`) | Mode | Tickets Covered | Feature Scope |
|---|---|---|---|
| `E2eTestHarness.kt` | Shared | `E2E-01` | Isolated container reset, `E2eCoordinator` HTTP client, `AbortCoordinatorOnFailureRule`, `BackendTestHelper`, Compose helpers |
| `single/AuthAndSettingsE2eTest.kt` | 1 Device | `E2E-02`, `E2E-09`, `E2E-11` | Email auto-register/login, whitespace/blank validation, wrong password rejection, email normalization, Google sign-in, display name edit/cancel/blank guard, top-bar leaves shortcut, version row, sign-out task wipe, permanent account deletion |
| `single/FocusRoomAndSummaryE2eTest.kt` | 1 Device | `E2E-03`, `E2E-05`, `E2E-11` | Task CRUD, blank guard, active switching, completed delete lock, uncheck unlocks delete, delete all fallback, `BackHandler` exit, persistence across re-entry, leaf rewards, 1-leaf floor, cumulative Journey balance |
| `single/ShopInventoryJourneyE2eTest.kt` | 1 Device | `E2E-07`, `E2E-08`, `E2E-05`, `E2E-11` | Seeded shop catalog, insufficient leaves, cancel unlock, `"Get Pass"` scroll-to-top, theme purchase with leaves, equip/unequip persistence across nav & sign-out/sign-in, Monthly/Yearly Supporter Pass, RevenueCat expiration webhook auto-revert to System Default, Restore Purchases |
| `single/FriendsSingleDeviceEdgeCasesE2eTest.kt` | 1 Device | `E2E-06`, `E2E-11` | Blank friend code ignored, Add Friend dialog cancel resets draft, Offline `ConnectionErrorDialog` (`"No Internet"`) & automatic recovery upon restoring network |
| `twodevice/TwoDeviceFocusRoomE2eTest.kt` | 2 Devices | `E2E-04`, `E2E-11` | Real 2-phone live presence, bi-directional task sync, leave/rapid re-entry dedup, same account on 2 devices simultaneously (no duplicate self card & synced leaves), display name change reflected upon re-entry |
| `twodevice/TwoDeviceFriendsAndSafetyE2eTest.kt` | 2 Devices | `E2E-06`, `E2E-09`, `E2E-11` | Self/unknown/duplicate errors, tag normalization, reject, accept, `"Already friends!"`, completed session story, mutual auto-accept, report + cancel, bidirectional real-time block in Friends & Focus Room, blocked request suppression, account deletion cascade removing friend |
| `threedevice/ThreeDeviceE2eTest.kt` | 3 Devices | `E2E-10` | 3 real users simultaneously in Focus Room + selective leave + live multi-peer task updates, asymmetric block isolation (A & B hide each other while C sees both), multiple simultaneous incoming requests (accept one, reject other) |

---

## 5. How to Run

### Prerequisites
- Python 3.10+ with `backend/requirements.txt` installed
- Android SDK (`adb`, `emulator`) with AVDs `Medium_Phone_API_37.0` and `Medium_Phone_API_37.0_2`
- JDK 17+ (e.g., Android Studio bundled JBR)

### One-Command Full Multi-Device E2E Run (Recommended)
From the repository root:

```bash
python scripts/run_e2e.py
```

Useful options:
- `--mode all|single|two|three` — Run all tiers (default `all`) or focus on 1-device, 2-device, or 3-device suites.
- `--scenario <substring>` — Filter to run a specific multi-device scenario or single-device test class.
- `--port 8010` — Port for the ephemeral local FastAPI backend (default: `8010`).
- `--coord-port 8011` — Port for the test-only multi-device coordinator HTTP server (default: `8011`).
- `--keep-emulators` — Keep newly booted emulators running after tests complete (for fast iteration).

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

1. **Per-Test State Reset:** Every `@Test` starts by calling `E2eTestHarness.launchMainActivity()`, which grants runtime permissions, clears `LocalTaskDataSource`, and resets `ServiceLocator.useDefaultContainer()`.
2. **Unique Accounts per Test:** Each test generates unique per-test emails (using a short random suffix) so tests never interfere with each other even when sharing one ephemeral run database.
3. **Fail-Fast Multi-Device Barriers:** Every 2-device and 3-device test class attaches `AbortCoordinatorOnFailureRule`; if any emulator throws or fails an assertion, it immediately calls `/abort/<runId>` so peer emulators fail fast instead of waiting for the full barrier timeout.
4. **Emulator 2 & 3 Wi-Fi Disablement:** `scripts/run_e2e.py` disables Wi-Fi and enables cellular data on emulators 2 and 3, then polls `dumpsys connectivity` until mobile data reports `NET_CAPABILITY_INTERNET` before launching instrumentation tests.
5. **Strict `am instrument` Parsing:** `adb shell am instrument -w` returns exit code `0` even on JUnit assertion failures, so `scripts/run_e2e.py` requires `"OK ("` and rejects `"FAILURES!!!"`, `"Process crashed"`, or `"INSTRUMENTATION_FAILED"`.
6. **Backend Logs Go to a File:** Uvicorn stdout/stderr is written to a temp log file (never an undrained pipe, which stalls the server on Windows), and the last 80 lines are printed if any test fails.
7. **API 36+/37 Emulators Need Espresso ≥ 3.7.0:** Older Espresso versions crash with `NoSuchMethodException: InputManager.getInstance` on Android 16/17 images.
8. **Wait for Snackbars & LazyColumn Settlements:** Wait for snackbars to dismiss before tapping FABs underneath them, and wait for list section headers (e.g., `Your Neighbourhood (1)`) + `waitForIdle()` before interacting with newly inserted cards.
