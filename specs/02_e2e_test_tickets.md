# Cozy Doubling — E2E Test Automation Work Items & Tickets

This backlog defines the ticket-style work items for full-stack **Android + FastAPI Backend End-to-End (E2E) Test Automation** across **1, 2, and 3 real Android devices/emulators**. Each ticket maps directly to the product backlog stories in [01_product_backlog.md](01_product_backlog.md) and [epics.md](epics.md), enumerates both **happy paths** and **critical edge cases**, and enforces **Clean Architecture** compliance.

---

## [E2E-01] Multi-Device E2E Harness, Coordinator, Isolated Backend & Clean Architecture Compliance
**Priority:** Critical  
**Type:** Infrastructure / Enabler  
**Linked Specs:** All Epics  
**Automated In:** `scripts/run_e2e.py`, `android/app/src/androidTest/java/com/grepho/cozydoubling/e2e/E2eTestHarness.kt`, `android/app/src/test/java/com/grepho/cozydoubling/ArchitectureBoundaryTest.kt`

### Description
As an engineer, I need a reproducible, one-command E2E test harness that boots an isolated live FastAPI backend plus up to 3 real Android emulators (with Wi-Fi disabled on emulators 2 and 3 to avoid the `AndroidWifi` collision) and synchronizes real multi-device instrumentation tests without violating clean architecture boundaries.

### Scope & Rules
- Starts Uvicorn (`app.main:app`) backed by a fresh ephemeral SQLite DB (`DATABASE_URL`) seeded via standard startup catalog seeding (`seed_catalog_if_empty`).
- Runs a test-only `ThreadingHTTPServer` coordinator (`E2eCoordinator`) offering `/kv` and `/barrier` and `/abort` endpoints over `adb reverse`.
- Boots/attaches up to 3 emulators (`Medium_Phone_API_37.0`, `Medium_Phone_API_37.0_2`, and `Medium_Phone_API_37.0 -read-only`), disables Wi-Fi and enables cellular data on emulators 2 & 3, disables animations, and grants `POST_NOTIFICATIONS`.
- Builds debug + androidTest APKs once with `-PapiBaseUrl=http://127.0.0.1:<port>` and installs across all devices.
- Uses **no simulated peer clients** for multi-user tests: all multi-user tests run real Android apps on 2 or 3 emulators concurrently.
- Production code outside `data/` and `core/di/` must never import `data.*`, `okhttp3.*`, or `@SerialName`.

### Acceptance Criteria
- [x] Running `python scripts/run_e2e.py` executes 1-device, 2-device, and 3-device suites and cleans up all temporary resources.
- [x] `ArchitectureBoundaryTest` passes with zero violations after any semantic `testTag` / UI updates.
- [x] Every E2E test resets `ServiceLocator` and `LocalTaskDataSource` cleanly before running.

---

## [E2E-02] Authentication & Account Lifecycle E2E
**Priority:** Critical  
**Type:** Feature E2E (Single Device)  
**Linked Specs:** `[AUTH-01]`, `[AUTH-02]`, `[AUTH-03]`, `[AUTH-06]`, `[AUTH-07]`  
**Automated In:** `e2e/single/AuthAndSettingsE2eTest.kt`

### Happy Path Scenarios
1. **New Email Auto-Registration & Login:** Open Reviewer Access dialog → enter new email & password → submit → lands on Home screen with `"Start Focus"` visible.
2. **Returning User Login (Case & Whitespace Normalization):** Sign out → re-open Reviewer Access dialog → enter same email with uppercase letters and leading/trailing whitespace → successfully authenticates into the same account.
3. **Google Sign-In:** Tap `"Sign in with Google"` on Login screen → authenticates via `/api/auth/google` → lands on Home screen.
4. **Sign Out & Local Task Wiping:** Add an unfinished task in Focus Room → leave room → open Settings → tap `"Sign Out"` → returns to Login screen; signing back in shows `"0/0 Done"` in Focus Room.
5. **Permanent Account Deletion:** Confirm `"Delete Account"` → returns to Login screen; re-registering the same email with a brand-new password succeeds.

### Edge Cases
1. **Blank / Whitespace Credentials Validation:** `"Login"` button remains disabled when email or password is blank or whitespace-only.
2. **Cancel Reviewer Dialog:** Tapping `"Cancel"` dismisses dialog cleanly with no error dialog.
3. **Wrong Password Rejection:** Incorrect password displays `"Incorrect password for this account."` and keeps user on Login screen.
4. **Cancel Account Deletion:** Tapping `"Cancel"` keeps user authenticated on Settings screen.

---

## [E2E-03] Live Focus Room — Task Management & Local Persistence E2E
**Priority:** Critical  
**Type:** Feature E2E (Single Device)  
**Linked Specs:** `[ROOM-01]`, `[ROOM-02]`, `[ROOM-03]`  
**Automated In:** `e2e/single/FocusRoomAndSummaryE2eTest.kt`

### Happy Path Scenarios
1. **Enter Focus Room:** Tap `"Start Focus"` → `"Cozy Room"`, `"1 focusing now"`.
2. **Add Tasks & Auto-Active Selection:** First added task becomes active; progress updates (`"0/1 Done"`, `"0/2 Done"`).
3. **Switch Active Task:** Tap another task row → self card updates active task text.
4. **Toggle Task Completion & Delete Unfinished Task:** Check checkbox → `"1/2 Done"`; delete unfinished task → active falls back cleanly.
5. **Unfinished Task Persistence Across Re-entry:** Leave room with unfinished task → re-enter → unfinished task restored, completed tasks cleared.
6. **Hardware Back Press (`BackHandler`):** Pressing system Back exits Focus Room cleanly via `finishWork()` and opens Summary.

### Edge Cases
1. **Blank / Whitespace Task Guard:** Submitting `"   "` does not create a task.
2. **Completed Task Delete Lock & Uncheck Unlock:** Completed task hides its delete button; unchecking it restores the delete button so it can be deleted.
3. **Delete All Tasks Fallback:** Deleting the last remaining task restores `"No active task • 0/0 done"` and `"0/0 Done"`.

---

## [E2E-04] Live Focus Room — Real 2-Device Presence & Sync E2E
**Priority:** Critical  
**Type:** Feature E2E (Real 2 Devices)  
**Linked Specs:** `[ROOM-01]`, `[ROOM-04]`  
**Automated In:** `e2e/twodevice/TwoDeviceFocusRoomE2eTest.kt`

### Happy Path Scenarios
1. **Real 2-Phone Mutual Presence:** Device A and Device B log into separate real Android apps and enter Focus Room → BOTH show `"2 focusing now"` and render each other's card.
2. **Bidirectional Live Task Sync:** Device B adds/completes/switches tasks via real Compose UI → Device A updates live; Device A adds/completes a task → Device B updates live.
3. **Leave & Re-Entry:** Device B exits to Summary → Device A drops to `"1 focusing now"`; Device B immediately re-enters → Device A updates back to `"2 focusing now"`.
4. **Display Name Change Reflected on Re-Entry:** Device B updates display name in Settings UI (`"BobRenamed"`, `"BobRenamedAgain"`) and enters Focus Room → Device A sees the updated name.

### Edge Cases
1. **Rapid Re-entry Deduplication:** Device B's card count on Device A remains `1` (`room_participant_card_<bobId>`) with zero duplicate ghost cards.
2. **Same Account on Two Devices Simultaneously:** Device A and Device B log into the exact same account and enter Focus Room together → both filter out own `userId` (`"1 focusing now"`), zero duplicate self cards, and session leaves accumulate cleanly across both devices.

---

## [E2E-05] Session Completion, Leaf Economy & Journey Stats E2E
**Priority:** High  
**Type:** Feature E2E (Single Device)  
**Linked Specs:** `[ECON-01]`, `[ECON-02]`, `[ECON-03]`, `[JOURN-01]`  
**Automated In:** `e2e/single/FocusRoomAndSummaryE2eTest.kt`, `e2e/single/ShopInventoryJourneyE2eTest.kt`

### Happy Path Scenarios
1. **Task-Based Reward Calculation:** 2 completed tasks → Summary displays `"6"` leaves and `"2 tasks"`.
2. **Cumulative Balance & Journey Reflection:** Multiple sessions accumulate leaves (`3 + 6 = 9`) visible on both Home top bar and Oasis `"Journey"` tab (`"Total Leaves"`, `"Time in Deep Focus"`).
3. **Home Leaf Capsule Shortcut:** Tapping the Home top bar leaf capsule navigates straight to Oasis → `"Shop"`.

### Edge Cases
1. **Minimum 1-Leaf Floor (0 Tasks, 0 Minutes):** Leaving immediately awards `1` leaf (`"0 tasks"`).

---

## [E2E-06] Friends & Social Activity E2E (Real 2 Devices)
**Priority:** High  
**Type:** Feature E2E (Real 2 Devices & Single Edge Cases)  
**Linked Specs:** `[SOC-01]`, `[SOC-02]`, `[SOC-03]`, `[SOC-04]`  
**Automated In:** `e2e/twodevice/TwoDeviceFriendsAndSafetyE2eTest.kt`, `e2e/single/FriendsSingleDeviceEdgeCasesE2eTest.kt`

### Happy Path Scenarios
1. **Display Own 8-Char Connect Code:** `"YOUR CONNECT CODE"` renders 8-character uppercase tag.
2. **Send Friend Request (Lowercase & `#` Normalization):** Device A sends `#<bobTag.lowercase()>` → `"Friend request sent!"`.
3. **Reject & Accept Flows on Real 2nd Phone:** Device B sees `"Pending Requests (1)"`, rejects → `"Your Neighbourhood (0)"`; Device A re-sends → Device B accepts → both see `"Your Neighbourhood (1)"` and `"Just joined! No sessions yet."`.
4. **Friend Completed Session Story:** Device B completes a real Focus Room session on Device B with `"Study algorithms"` → Device A refreshes Friends and sees `"Study algorithms"` on Device B's card.
5. **Mutual Request Auto-Acceptance:** Device A sends request to Device B; Device B sends request back to Device A without clicking Accept → both see `"Your Neighbourhood (1)"`.

### Edge Cases
1. **Self-Add, Non-Existent Tag, Duplicate Outgoing & `"Already friends!"` Rejections:** Verified end-to-end against live backend responses.
2. **Blank Code Ignored & Cancel Resets Input:** Submitting whitespace does not emit a snackbar; canceling clears the draft text.
3. **Cascade Account Deletion Removes Friend:** When Device B deletes its account in Settings UI, Device A refreshes Friends and sees `"Your Neighbourhood (0)"`.

---

## [E2E-07] Shop & Supporter Subscriptions E2E
**Priority:** High  
**Type:** Feature E2E (Single Device)  
**Linked Specs:** `[SHOP-01]`, `[SHOP-02]`, `[SHOP-03]`, `[SHOP-04]`  
**Automated In:** `e2e/single/ShopInventoryJourneyE2eTest.kt`

### Happy Path Scenarios
1. **Browse Seeded Catalog:** `"Cozy Supporter"` (`"Monthly"`, `"Yearly"`, `"Lifetime"`), `"Peach Blossom"`, `"Midnight Velvet"`.
2. **Purchase Basic Theme with Leaves:** Purchase `"Peach Blossom"` for `100` leaves → balance deducted, unlocks in Shop & Inventory.
3. **Purchase Monthly & Yearly Supporter Passes:** Syncs with `/api/subscriptions/sync`, unlocks `"Midnight Velvet"`, enables `"Manage Subscription"`.
4. **Restore Purchases in Settings:** Calls `/api/subscriptions/sync` and maintains supporter perks.

### Edge Cases
1. **Insufficient Leaves Purchase Attempt & Cancel Dialog:** 0 leaves rejects purchase cleanly; Cancel aborts without purchase.
2. **`"Get Pass"` Scroll-to-Top Shortcut:** Tapping `"Get Pass"` on a premium theme scrolls Shop back to `"Cozy Supporter"`.
3. **Supporter Pass Expiration Webhook Auto-Revert:** `POST /api/subscriptions/webhook` (`EXPIRATION`) removes `"Midnight Velvet"`, auto-reverts equipped theme to `"System Default"`, preserves leaf-purchased `"Peach Blossom"`, and hides `"Manage Subscription"`.

---

## [E2E-08] Inventory & Theme Customization E2E
**Priority:** High  
**Type:** Feature E2E (Single Device)  
**Linked Specs:** `[THEME-01]`, `[THEME-02]`, `[THEME-03]`, `[THEME-04]`  
**Automated In:** `e2e/single/ShopInventoryJourneyE2eTest.kt`

### Happy Path & Edge Case Scenarios
1. **Default Inventory State:** `"System Default"` equipped by default (`"1 Themes"`).
2. **Equip Purchased & Premium Themes + Revert:** Equip `"Peach Blossom"` → `"System Default"` → `"Midnight Velvet"`.
3. **Persistence Across Navigation, Sign-Out & Sign-In:** Equipped `"Peach Blossom"` remains equipped after switching tabs, signing out cleanly, and signing back in.

---

## [E2E-09] User Safety, Moderation & Real-Time Block Filtering E2E (Real 2 Devices)
**Priority:** High  
**Type:** Feature E2E (Real 2 Devices)  
**Linked Specs:** `[AUTH-05]`, `[SAFE-01]`, `[SAFE-02]`, `[SAFE-03]`  
**Automated In:** `e2e/single/AuthAndSettingsE2eTest.kt`, `e2e/twodevice/TwoDeviceFriendsAndSafetyE2eTest.kt`

### Happy Path & Edge Case Scenarios
1. **Display Name Update, Blank Rejection & Cancel Discard:** Verified in Settings + Home + Journey.
2. **Report User & Cancel Report Dialog:** Verified from Friends options menu (`"Report submitted. Thank you."`).
3. **Bidirectional Real-Time Block from Friends & Focus Room:** When Device A blocks Device B in Friends UI, **both Device A and Device B** update immediately to `"Your Neighbourhood (0)"` via `/ws/safety`, neither sees the other in Focus Room, and Device B's new friend requests to Device A remain suppressed. When Device A blocks Device B inside a live Focus Room, **both Device A and Device B** immediately drop to `"1 focusing now"`.

---

## [E2E-10] 3-User Real Multi-Device Focus Room, Asymmetric Safety Isolation & Multi-Request Friends E2E
**Priority:** Critical  
**Type:** Feature E2E (Real 3 Devices)  
**Linked Specs:** `[ROOM-01]`, `[ROOM-04]`, `[SOC-02]`, `[SAFE-01]`, `[SAFE-02]`  
**Automated In:** `e2e/threedevice/ThreeDeviceE2eTest.kt`

### Description
Verify 3-participant concurrency across 3 real Android emulators connected simultaneously to the live backend.

### Happy Path & Edge Case Scenarios
1. **3-User Simultaneous Focus Room, Live Task Sync & Selective Leave:** Devices A, B, and C enter Focus Room → all 3 assert `"3 focusing now"` and see both peers' live tasks (`"Bob writing code • 1/1 done"`, `"Charlie reading • 0/2 done"`) → Device C leaves → Devices A & B both drop to `"2 focusing now"` while continuing to see each other.
2. **3-User Asymmetric Block Isolation:** Devices A, B, and C are all in Focus Room (`"3 focusing now"`). Device A blocks Device B → Device A sees only Device C (`"2 focusing now"`), Device B sees only Device C (`"2 focusing now"`), and **Device C still sees BOTH Device A and Device B (`"3 focusing now"`)**, including Device B's subsequent task updates.
3. **3-User Multiple Pending Requests (Accept One, Reject Other):** Devices B and C both send friend requests to Device A → Device A sees `"Pending Requests (2)"`, accepts Device B (`pending_accept_<bobTag>`) and rejects Device C (`pending_reject_<charlieTag>`) → Device A & B see `"Your Neighbourhood (1)"` while Device C sees `"Your Neighbourhood (0)"`.

---

## [E2E-11] Offline Connectivity Recovery & Cross-Cutting Edge Cases E2E
**Priority:** High  
**Type:** Resilience & Edge Cases  
**Linked Specs:** Network / Connectivity Observer, `[AUTH-04]`  
**Automated In:** `e2e/single/FriendsSingleDeviceEdgeCasesE2eTest.kt`

### Happy Path & Edge Case Scenarios
1. **Device Offline & Automatic Recovery (`ConnectionErrorDialog`):** While authenticated on Home, disable Wi-Fi and mobile data via ADB shell (`svc wifi disable`, `svc data disable`) → `"No Internet"` blocking dialog with `"Retry Connection"` appears automatically → re-enable data and Wi-Fi → `"No Internet"` dialog disappears automatically and `"Start Focus"` returns to interactive state.
