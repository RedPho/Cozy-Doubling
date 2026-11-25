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