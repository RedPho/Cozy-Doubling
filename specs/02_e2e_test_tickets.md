# Cozy Doubling — E2E Test Automation Work Items & Tickets

This backlog defines the ticket-style work items for full-stack **Android + FastAPI Backend End-to-End (E2E) Test Automation**. Each ticket maps directly to the product backlog stories in [01_product_backlog.md](01_product_backlog.md) and [epics.md](epics.md), enumerates both **happy paths** and **critical edge cases**, and enforces **Clean Architecture** compliance.

---

## [E2E-01] E2E Test Harness, Isolated Live Backend & Clean Architecture Compliance
**Priority:** Critical  
**Type:** Infrastructure / Enabler  
**Linked Specs:** All Epics  
**Automated In:** `scripts/run_e2e.py`, `android/app/src/androidTest/java/com/grepho/cozydoubling/e2e/E2eTestHarness.kt`, `android/app/src/test/java/com/grepho/cozydoubling/ArchitectureBoundaryTest.kt`

### Description
As an engineer, I need a reproducible, one-command E2E test harness that boots an isolated live FastAPI backend and executes Jetpack Compose instrumentation tests against the real Android app without violating clean architecture boundaries.

### Scope & Rules
- Starts Uvicorn (`app.main:app`) backed by a fresh ephemeral SQLite DB (`DATABASE_URL`) seeded via standard startup catalog seeding (`seed_catalog_if_empty`).
- Configures `adb reverse tcp:<port> tcp:<port>` and passes `-PapiBaseUrl=http://127.0.0.1:<port>` so the Android app reaches the live backend reliably without emulator network conflicts.
- Resets `ServiceLocator` (`DefaultAppContainer`) and `LocalTaskDataSource` between every test case.
- Provides a test-only `BackendPeerClient` inside `androidTest` for real-time multi-user scenarios (User B).
- Production code outside `data/` and `core/di/` must never import `data.*`, `okhttp3.*`, or `@SerialName`.

### Acceptance Criteria
- [x] Running `python scripts/run_e2e.py` starts the backend, waits for `/health`, attaches/boots an emulator, executes `connectedDebugAndroidTest`, and cleans up all temporary resources.
- [x] `ArchitectureBoundaryTest` passes with zero violations after any semantic `testTag` / UI updates.
- [x] Every E2E test is self-contained and does not leak auth tokens or local tasks into subsequent tests.

---

## [E2E-02] Authentication & Account Lifecycle E2E
**Priority:** Critical  
**Type:** Feature E2E  
**Linked Specs:** `[AUTH-01]`, `[AUTH-02]`, `[AUTH-03]`, `[AUTH-06]`, `[AUTH-07]`  
**Automated In:** `AuthAndSettingsE2eTest.kt`

### Description
Verify complete user authentication flows (email/password auto-registration, returning login, Google sign-in), input normalization and error handling, clean sign-out, and permanent cascade account deletion across Android UI and FastAPI backend.

### Happy Path Scenarios
1. **New Email Auto-Registration & Login:** Open Reviewer Access dialog → enter new email & password → submit → lands on Home screen with `"Start Focus"` visible and backend profile initialized.
2. **Returning User Login (Case & Whitespace Normalization):** Sign out → re-open Reviewer Access dialog → enter same email with uppercase letters and leading/trailing whitespace → successfully authenticates into the same account.
3. **Google Sign-In:** Tap `"Sign in with Google"` on Login screen → authenticates via `/api/auth/google` → lands on Home screen.
4. **Sign Out & Local Task Wiping:** Add an unfinished task in Focus Room → leave room → open Settings → tap `"Sign Out"` → returns to Login screen; signing back in shows an empty task list in Focus Room (local tasks wiped).
5. **Permanent Account Deletion:** Open Settings → tap `"Delete Account"` → confirm `"Delete"` → returns to Login screen; backend deletes account (`DELETE /api/auth/account`), so authenticating with the same email + a different password creates a brand-new account instead of failing with wrong password.

### Edge Cases
1. **Empty Credentials Validation:** In Reviewer Access dialog, `"Login"` button remains disabled when email or password is blank.
2. **Wrong Password Rejection:** Attempt login for an existing email with an incorrect password → Login Error alert displays `"Incorrect password for this account."` and user remains on Login screen.
3. **Cancel Account Deletion:** Tap `"Delete Account"` → tap `"Cancel"` → stays authenticated on Settings screen and account remains intact.

### Acceptance Criteria
- [x] All 5 happy paths and 3 edge cases pass end-to-end against live `/api/auth/*` endpoints.

---

## [E2E-03] Live Focus Room — Task Management & Local Persistence E2E
**Priority:** Critical  
**Type:** Feature E2E  
**Linked Specs:** `[ROOM-01]`, `[ROOM-02]`, `[ROOM-03]`  
**Automated In:** `FocusRoomAndSummaryE2eTest.kt`

### Description
Verify single-user Focus Room session creation, task CRUD rules, active task selection, completed-task immutability, and offline unfinished-task persistence across sessions.

### Happy Path Scenarios
1. **Enter Focus Room:** Tap `"Start Focus"` from Home → enters `"Cozy Room"`, displays `"1 focusing now"`, and renders self participant card.
2. **Add First & Second Tasks:** Add `"Write E2E docs"` (auto-selected as active task; progress pill shows `"0/1 Done"`) and add `"Run emulator suite"` (`"0/2 Done"`).
3. **Switch Active Task:** Tap second task row → self participant card updates active task text to `"Run emulator suite"`.
4. **Toggle Task Completion:** Check first task's checkbox → progress pill updates to `"1/2 Done"`.
5. **Delete Unfinished Active Task:** Delete `"Run emulator suite"` → task disappears, progress updates to `"1/1 Done"`, and active task falls back cleanly.
6. **Unfinished Task Persistence Across Re-entry:** Leave room with 1 completed task and 1 unfinished task → finish summary → re-enter Focus Room → unfinished task is restored while completed task is NOT carried over.

### Edge Cases
1. **Blank / Whitespace Task Guard:** Submitting empty or whitespace-only text (`"   "`) via the add button does not create a task (`"0/0 Done"` unchanged).
2. **Completed Task Cannot Be Deleted:** Once a task is checked complete, its delete button is hidden/removed from the UI so completed tasks remain intact for reward accounting.

### Acceptance Criteria
- [x] All task state transitions update both the Task Bottom Sheet and self `ParticipantCard` accurately.
- [x] Unfinished tasks persist across session re-entry and are cleared on sign-out.

---

## [E2E-04] Live Focus Room — Real-Time Multi-User Presence & Sync E2E
**Priority:** Critical  
**Type:** Feature E2E  
**Linked Specs:** `[ROOM-01]`, `[ROOM-04]`  
**Automated In:** `FocusRoomAndSummaryE2eTest.kt`

### Description
Verify real-time WebSocket (`/ws/room`) body-doubling presence between User A (real Android UI) and User B (live peer connected to the same backend), including live task updates, disconnect cleanup, and rapid re-entry deduplication.

### Happy Path Scenarios
1. **Peer Joins Live Room:** User A is in Focus Room (`"1 focusing now"`) → Peer B connects to `/ws/room` → User A's UI updates to `"2 focusing now"` and displays Peer B's participant card with Peer B's name and task.
2. **Peer Broadcasts Live Task Progress:** Peer B sends a `presence_update` with a new task and completed count (`1/2`) → User A's grid updates Peer B's card text live without leaving the room.
3. **Peer Leaves Room:** Peer B closes WebSocket → Peer B's card disappears from User A's Focus Room and count returns to `"1 focusing now"`.

### Edge Cases
1. **Rapid Peer Re-entry Deduplication:** Peer B disconnects and immediately reconnects with a new session presence key (`userId:newSessionId`) → User A sees exactly **one** card for Peer B (`"2 focusing now"`), never duplicate ghost cards.

### Acceptance Criteria
- [x] Real WebSocket events (`presence_state`, `presence_update`, `presence_leave`) drive live Compose UI state end-to-end.

---

## [E2E-05] Session Completion, Leaf Economy & Journey Stats E2E
**Priority:** High  
**Type:** Feature E2E  
**Linked Specs:** `[ECON-01]`, `[ECON-02]`, `[ECON-03]`, `[JOURN-01]`  
**Automated In:** `FocusRoomAndSummaryE2eTest.kt`, `ShopInventoryJourneyE2eTest.kt`

### Description
Verify server-computed session rewards, post-session Summary screen rendering, atomic leaf balance increments on the top bar, and lifetime Journey dashboard metrics.

### Happy Path Scenarios
1. **Task-Based Reward Calculation:** Complete 2 tasks in Focus Room → tap Leave → Summary screen displays `"Great job showing up today!"`, `"2 tasks"` finished, and `"6"` leaves earned (`0*1 + 2*3 = 6`).
2. **Return Home & Top-Bar Balance Sync:** Tap `"Continue"` on Summary → returns to Home with top-bar leaf capsule reflecting the earned leaves.
3. **Journey Dashboard Reflection:** Navigate to Oasis → `"Journey"` tab → displays `"<displayName>'s Oasis"`, `"Cozy Doubler since YYYY-MM"`, `"Total Leaves"`, and `"Time in Deep Focus"` matching backend profile state.

### Edge Cases
1. **Minimum 1-Leaf Floor (0 Tasks, 0 Minutes):** Enter Focus Room and immediately leave without completing any tasks → backend awards minimum `1` leaf and Summary screen displays `1` under `"LEAVES EARNED"` and `"0 tasks"` under `"Tasks Finished"`.

### Acceptance Criteria
- [x] Summary stats come from real `/api/sessions/finish` + `/api/sessions/{id}` responses and propagate to Home top bar and Journey screen.

---

## [E2E-06] Friends & Social Activity E2E
**Priority:** High  
**Type:** Feature E2E  
**Linked Specs:** `[SOC-01]`, `[SOC-02]`, `[SOC-03]`, `[SOC-04]`  
**Automated In:** `FriendsAndSafetyE2eTest.kt`

### Description
Verify player tag discovery, friend request sending/accepting/rejecting, mutual auto-acceptance, error edge cases, and friend activity stories (both with and without completed sessions).

### Happy Path Scenarios
1. **Display Own 8-Character Connect Code:** Open Oasis → `"Friends"` tab → displays `"YOUR CONNECT CODE"` with user's 8-char uppercase player tag.
2. **Send Friend Request (Lowercase & `#` Prefix Normalization):** Tap `"Add Friend"` FAB → enter Peer B's tag in lowercase prefixed with `#` (e.g., `#abcd1234`) → tap `"Send Request"` → snackbar confirms `"Friend request sent!"`.
3. **Incoming Request — Accept Flow & No-Session Story:** Peer C sends a request to User A → User A sees `"Pending Requests (1)"` → taps `"Accept"` → Peer C moves to `"Your Neighbourhood (1)"` displaying `"Just joined! No sessions yet."` when Peer C has not completed a session.
4. **Friend Completed Session Story:** Peer C completes a backend focus session with last task `"Study algorithms"` → User A refreshes Friends → Peer C's card displays the last focus story containing `"Study algorithms"`.
5. **Incoming Request — Reject Flow:** Peer D sends a request to User A → User A taps `"Reject"` → request disappears and Peer D is NOT added to `"Your Neighbourhood"`.
6. **Mutual Request Auto-Acceptance:** Peer E sends a request to User A → instead of clicking Accept, User A sends a friend request to Peer E's tag via `"Add Friend"` → backend auto-accepts and Peer E appears directly in `"Your Neighbourhood"`.

### Edge Cases
1. **Self-Add Rejection:** Entering User A's own connect code displays error snackbar (`"You can't add yourself!"`) and does not create a self-friendship.
2. **Non-Existent Tag Rejection:** Entering `"ZZZZ9999"` displays error snackbar (`"User not found"`).
3. **Duplicate Pending Request Rejection:** Re-sending a request to a user with an already pending outgoing request displays error snackbar (`"Request already sent!"`).

### Acceptance Criteria
- [x] All friend request happy paths, mutual auto-accept, activity stories, and backend error messages surface cleanly in Android UI.

---

## [E2E-07] Shop & Supporter Subscriptions E2E
**Priority:** High  
**Type:** Feature E2E  
**Linked Specs:** `[SHOP-01]`, `[SHOP-02]`, `[SHOP-03]`, `[SHOP-04]`  
**Automated In:** `ShopInventoryJourneyE2eTest.kt`

### Description
Verify browsing the seeded backend shop catalog, leaf balance validation, basic theme purchase, Supporter Pass subscription activation, and Restore Purchases in Settings.

### Happy Path Scenarios
1. **Browse Seeded Catalog:** Open Oasis → `"Shop"` tab → renders `"Cozy Supporter"` card (`"Monthly"`, `"Yearly"`, `"Lifetime"`), `"Peach Blossom"` (`"100 Leaves"`), and `"Midnight Velvet"` (`"Get Pass"`).
2. **Purchase Basic Theme with Leaves:** With ≥ 100 leaves earned through real sessions, tap `"100 Leaves"` on `"Peach Blossom"` → confirmation dialog `"Unlock Theme"` appears → tap `"Unlock"` → leaf balance decreases by 100, `"Peach Blossom"` shows owned state in Shop and appears in Inventory.
3. **Purchase Supporter Pass:** Tap `"Monthly"` on `"Cozy Supporter"` → syncs supporter status with `/api/subscriptions/sync` → banner transitions to active supporter message (`"Thank you for being a supporter!"`), `"Midnight Velvet"` unlocks, and `"Manage Subscription"` appears in Settings.
4. **Restore Purchases in Settings:** Open Settings → tap `"Restore Purchases"` → calls `/api/subscriptions/sync` and keeps Supporter status active.

### Edge Cases
1. **Insufficient Leaves Purchase Attempt:** New user with 0 leaves taps `"100 Leaves"` on `"Peach Blossom"` and confirms `"Unlock"` → backend rejects with 400 (`"Not enough leaves."`), balance stays `0`, `"Peach Blossom"` remains locked (`"100 Leaves"` still shown) and is absent from Inventory.
2. **Cancel Theme Unlock Dialog:** Tap `"100 Leaves"` → tap `"Cancel"` → dialog closes without deducting leaves or purchasing.

### Acceptance Criteria
- [x] Real backend `/api/shop/items`, `/api/shop/buy`, and `/api/subscriptions/sync` enforce prices, ownership, and supporter perks end-to-end.

---

## [E2E-08] Inventory & Theme Customization E2E
**Priority:** High  
**Type:** Feature E2E  
**Linked Specs:** `[THEME-01]`, `[THEME-02]`, `[THEME-03]`, `[THEME-04]`  
**Automated In:** `ShopInventoryJourneyE2eTest.kt`

### Description
Verify owned theme listing, equipping purchased and supporter-unlocked themes, reverting to System Default, and verifying equipped state persists across screen navigation and backend profile reloads.

### Happy Path Scenarios
1. **Default Inventory State:** New user opens Oasis → `"Inventory"` tab → displays `"Owned Themes"`, `"1 Themes"`, `"System Default"` with `"Equipped"` badge.
2. **Equip Purchased Basic Theme:** After purchasing `"Peach Blossom"`, Inventory displays `"2 Themes"` → tap `"Apply"` on `"Peach Blossom"` → `"Equipped"` badge moves to `"Peach Blossom"` and persists after navigating to Home and returning to Inventory.
3. **Unequip & Revert to System Default:** Tap `"Apply"` on `"System Default"` → calls `/api/inventory/unequip` → `"Equipped"` badge returns to `"System Default"`.
4. **Supporter Unlocks & Equips Premium Theme:** After activating Supporter Pass, Inventory displays `"Midnight Velvet"` → tap `"Apply"` on `"Midnight Velvet"` → `"Equipped"` badge moves to `"Midnight Velvet"`.

### Edge Cases
1. **Persisted Theme After Profile Refresh:** Navigating away to Settings/Home and back to Inventory preserves the equipped custom theme without reverting to `"System Default"`.

### Acceptance Criteria
- [x] Equipping and unequipping themes calls real `/api/inventory/equip` and `/api/inventory/unequip` endpoints and updates UI state deterministically.

---

## [E2E-09] User Safety, Moderation & Settings Profile E2E
**Priority:** High  
**Type:** Feature E2E  
**Linked Specs:** `[AUTH-05]`, `[SAFE-01]`, `[SAFE-02]`, `[SAFE-03]`  
**Automated In:** `AuthAndSettingsE2eTest.kt`, `FriendsAndSafetyE2eTest.kt`

### Description
Verify display name editing (and blank rejection), conduct violation reporting, and real-time user blocking across both Focus Room and Friends screens.

### Happy Path Scenarios
1. **Update Display Name:** Open Settings → tap `"Display Name"` → edit to `"SageFocus"` → tap `"Save"` → Settings, Journey (`"SageFocus's Oasis"`), and Focus Room self card all reflect `"SageFocus"`.
2. **Report User from Friends:** Open friend card `"More options"` menu → tap `"Report User"` → select `"Inappropriate Name"` → backend records report (`POST /api/safety/report`) and snackbar displays `"Report submitted. Thank you."`.
3. **Block User from Focus Room (Real-Time Removal):** User A and Peer B are in Focus Room (`"2 focusing now"`) → User A opens Peer B's `"Options"` menu → taps `"Block User"` → Peer B's card immediately disappears and count drops to `"1 focusing now"`.
4. **Block User from Friends (Immediate Filtering + Room Suppression):** User A opens accepted friend Peer C's `"More options"` menu → taps `"Block User"` → Peer C disappears immediately from `"Your Neighbourhood"`; if Peer C enters the Focus Room afterward, User A does not see Peer C.

### Edge Cases
1. **Blank Display Name Guard:** Editing Display Name to whitespace (`"   "`) and tapping `"Save"` does NOT overwrite the existing display name.
2. **Cancel Report Dialog:** Opening `"Report User"` and tapping `"Cancel"` dismisses the dialog without submitting a report.
3. **Real-Time Reciprocal Block via Safety WebSocket:** When Peer D blocks User A on the backend while both are in the same Focus Room, `/ws/safety` notifies User A and Peer D disappears from User A's Focus Room in real time without restarting the app.

### Acceptance Criteria
- [x] Display name changes persist in `/api/profile`.
- [x] Blocking immediately filters users from both Focus Room and Friends lists in real time via `/api/safety/block` and `/ws/safety`.
