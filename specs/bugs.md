# BUG-1: adding friends while the user is blocked (`Resolved`)

## Description
As a user, I shouldn't be able to add a friend while the user is blocked (either direction), and I should see a clear explanation when a friend action fails.

## Fix
- **Backend ([friends.py](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/backend/app/routers/friends.py)):** Added bidirectional block validation (`get_block_direction`) in `POST /api/friends/request` and `POST /api/friends/{sender_id}/accept`, returning `HTTP 403` with clear UX guidance (`"You have blocked this user. Unblock them in Settings to add them."` / privacy-safe `"Unable to send friend request to this user."`).
- **Android ([RemoteFriendsRepository.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/data/remote/RemoteFriendsRepository.kt), [FakeFriendsRepository.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/data/fake/FakeFriendsRepository.kt), [FriendsViewModel.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/features/friends/FriendsViewModel.kt), [RemoteSafetyRepository.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/data/remote/RemoteSafetyRepository.kt)):** Propagated server/network errors with `getOrThrow()` instead of silently dropping failures, added real-time blocked-user filtering in `FriendsViewModel`, added optimistic rollback on block failures, and surfaced snackbar messages across all Friend/Safety actions.

---

# Bug-2: New friend requests isn't shown before restarting the app (`Resolved`)

## Description
Incoming friend requests, accepted friendships, and removals did not appear until restarting the app.

## Fix
- **Backend ([friends_manager.py](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/backend/app/websockets/friends_manager.py), [main.py](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/backend/app/main.py), [friends.py](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/backend/app/routers/friends.py), [safety.py](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/backend/app/routers/safety.py)):** Added `/ws/friends` WebSocket pushing `request_received`, `request_accepted`, `request_rejected`, `friend_removed`, and `friends_updated` immediately when any friendship or block changes.
- **Android ([RemoteFriendsRepository.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/data/remote/RemoteFriendsRepository.kt), [FriendsPage.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/features/friends/FriendsPage.kt), [OasisPage.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/features/oasis/OasisPage.kt)):** Subscribed to `/ws/friends` on authentication with exponential-backoff reconnect, refreshed on Friends tab open + pull-to-refresh (`PullToRefreshBox`), and added a live pending-requests count `Badge` on the `Friends` sub-tab in Oasis.

---

# Bug-3: Sometimes, starting a new session opens the end session summary, like it stops the last one but not good. (`Resolved`)

## Description
Starting a fresh focus session from Home could occasionally jump straight to the End Session Summary of an old session or prematurely finish the new session.

## Root Causes & Fix
- **Explicit Start vs Cold-Start Recovery ([Routes.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/core/navigation/Routes.kt), [AppNavHost.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/core/navigation/AppNavHost.kt), [FocusRoomViewModel.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/features/room/FocusRoomViewModel.kt)):** Separated explicit Focus Room navigation (`recovery = false`) from OS process-death / notification recovery (`recovery = true`) so stale persisted session IDs are purged on explicit "Start Focus" instead of emitting `autoFinishEvents` for the old session. Added `isFinishing` & coroutine job cancellation guards around suspension points in `startRoom()`, `finishWork()`, and `onCleared()`.
- **Synchronous Storage & Guarded Snapshots ([AndroidFocusSessionController.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/data/local/AndroidFocusSessionController.kt), [NoOpFocusSessionLifecycleController.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/data/fake/NoOpFocusSessionLifecycleController.kt)):** Used synchronous `.commit()` on `stopSession()`/`persistSession()` and guarded `updateSession()` so a stopped or mismatched session snapshot can never re-persist an ended session.
- **Session-Scoped Actions & Events ([FocusSessionLifecycleController.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/core/room/FocusSessionLifecycleController.kt), [FocusSessionService.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/data/local/FocusSessionService.kt), [MainActivity.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/core/MainActivity.kt), [RemoteFocusRoomRepository.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/data/remote/RemoteFocusRoomRepository.kt)):** Scoped `FocusNotificationAction` and notification `PendingIntent` extras by `sessionId`, consumed intent extras after handling in `MainActivity`, ignored `SessionEndedByServer` events not matching the active `sessionId`, and generation-guarded `RemoteFocusRoomRepository` WebSocket callbacks and `leaveRoom(presenceKey)`.

---

# Bug-4: Offline-gap Summary mislabeling & room timer drift from server-verified time (`Resolved`)

## Description
1. A 10–30 minute disconnect set `was_capped = True` on completion without changing `end_reason`, causing `SummaryPage` to display the misleading **12-hour cap** notice instead of an offline-gap explanation. Reconnecting after a 10–30 minute gap also did not mark `was_capped`.
2. The Focus Room timer was purely local and did not sync with the server's verified `active_seconds`, nor did the Focus Room show a subtle `Connecting…` / `Reconnecting…` indicator.
3. `RemoteFocusRoomRepository.finishSession` silently ignored HTTP/network failures, and `SummaryViewModel` displayed `0m / 0 leaves` success if `fetchSession` returned an unprocessed session.

## Fix
- **Backend ([session_lifecycle.py](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/backend/app/services/session_lifecycle.py), [main.py](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/backend/app/main.py)):** Added `end_reason = "completed_with_offline_gap"` for sessions that excluded >10m offline gaps, marked `was_capped = True` in `record_ws_connect` when skipping >10m offline/startup gaps, and pushed server-verified `active_seconds` via `session_sync` on `/ws/room` connect and inside `pong` responses.
- **Android ([FocusRoomModels.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/features/room/FocusRoomModels.kt), [RemoteFocusRoomRepository.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/data/remote/RemoteFocusRoomRepository.kt), [FocusRoomViewModel.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/features/room/FocusRoomViewModel.kt), [FocusRoomPage.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/features/room/FocusRoomPage.kt), [SummaryModels.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/features/summary/SummaryModels.kt), [SummaryViewModel.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/features/summary/SummaryViewModel.kt), [SummaryPage.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/features/summary/SummaryPage.kt)):** Synced Focus Room timer, PiP widget, and notification chronometer from `SessionTimeSynced`, surfaced a subtle `Connecting…` / `Reconnecting…` badge, propagated + cached `/finish` results cleanly, treated unprocessed sessions as `loadError` in `SummaryViewModel`, and extracted `resolveSummaryPresentation` so `"capped_max_duration"` vs `"completed_with_offline_gap"` is strictly separated and unit-tested.

# Bug-5: when clicked fullscreen in pip mode, it entered home screen. even though it reconnects last session when clicked start session, it should just seen last session when clicked pip mode. (`Resolved`)

## Description
When entering Picture-in-Picture mode from Focus Room and tapping fullscreen, the app opened the Home screen instead of returning to the active Focus Room session.

## Root Cause & Fix
- **Unmounted Navigation Host During PiP ([MainActivity.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/core/MainActivity.kt), [FocusSessionLifecycleController.kt](file:///c:/Users/emin.m.dasdan/repos/Cozy-Doubling/android/app/src/main/java/com/grepho/cozydoubling/core/room/FocusSessionLifecycleController.kt)):** `MainActivity` previously toggled between `FocusPipOverlay` and `CozyDoublingApp` using `if (isInPipMode && snapshot != null) ... else`, unmounting `CozyDoublingApp` and destroying `rememberNavController()` + the `FocusRoom` backstack on PiP entry. Updated `MainActivity` to always keep `CozyDoublingApp` mounted underneath `FocusPipOverlay`, and added `shouldRestoreFocusRoomOnPipExit` in `onPictureInPictureModeChanged` so expanding PiP to fullscreen restores/preserves Focus Room cleanly while PiP dismissal (`X`) does not trigger spurious navigation.