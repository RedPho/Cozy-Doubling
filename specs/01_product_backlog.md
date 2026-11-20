# Cozy Doubling — Product Backlog & Specification

> **Source of Truth:** Clean, product-level backlog describing existing system behavior, constraints, and business rules without migration framing or vendor-specific terminology.

---

# Epic: Authentication & Account Lifecycle

**Goal:** Enable users to seamlessly register, authenticate across devices, maintain persistent sessions, customize profile identity, and control their account lifecycle up to complete deletion.  
**Scope:**
- Google One-Tap/Native Authentication
- Email & Password Login
- Automatic Profile and Player Tag Provisioning
- Session Persistence and Graceful Session Recovery
- Display Name Customization
- Clean Sign-Out and Local Data Sanitization
- Permanent Account Deletion

---

## [AUTH-01] Sign In with Google
**Priority:** High

### Description
As a Cozy Doubling user, I want to authenticate using my Google account so that I can instantly access the app without remembering a separate password.

### Business Rules & Constraints
- Authenticating via Google is supported for both returning users and first-time users.
- Each Google account maps to exactly one user account.
- If the Google account has never signed in before, the system must automatically provision a new user profile (see [AUTH-03]).
- If the account already exists, the user's existing profile, balance, inventory, and stats are restored.
- An unverified or expired Google identity credential must be rejected.

### Acceptance Criteria
- [ ] Given a user with an existing account linked to Google, when they authenticate with Google, then their active session is restored and they are routed to the Home screen.
- [ ] Given a first-time user, when they authenticate with Google, then an account and profile are created immediately and they are routed to the Home screen.
- [ ] Given invalid, expired, or cancelled Google credentials, when sign-in is attempted, then authentication is rejected, an error message is displayed, and the user remains on the Login screen.
- [ ] Given two simultaneous sign-in requests for the same new Google account, then exactly one user account is created and both requests resolve to that account.

### Open Questions
- None.

---

## [AUTH-02] Sign In with Email & Password
**Priority:** High

### Description
As a user on a device without Google services, I want to sign in with an email address and password so that I can access my account.

### Business Rules & Constraints
- Email addresses must be handled case-insensitively and trimmed of leading/trailing whitespace.
- Passwords must be validated against secure hashed storage.
- Generic error messaging must be returned on failure to prevent user enumeration (an external party cannot determine whether the email exists or the password was wrong).
- Repeated consecutive failed attempts must trigger temporary rate limiting to protect against brute-force attacks.

### Acceptance Criteria
- [ ] Given a registered user, when they provide their exact registered email and password, then authentication succeeds and they are navigated to the Home screen.
- [ ] Given an email entered in uppercase or with surrounding whitespace (e.g., `" USER@DOMAIN.COM "`), when submitted with the correct password, then authentication succeeds.
- [ ] Given an incorrect password for a registered email, when login is attempted, then authentication fails with `"Invalid email or password"`.
- [ ] Given an unregistered email address, when login is attempted, then authentication fails with the identical `"Invalid email or password"` message.
- [ ] Given an empty email, empty password, or malformed email format, when submitted, then client validation fails with a clear message and no login attempt is dispatched.
- [ ] Given 5 consecutive failed login attempts within 60 seconds, when a 6th attempt is made, then the user is temporarily locked out with a rate-limit notice.

### Open Questions
- None. (Self-serve email registration and guest access are planned enhancements; see [AUTH-08]).

---

## [AUTH-08] Guest Access & Self-Serve Email Registration (Planned Enhancement)
**Priority:** Low

### Description
As a new visitor, I want the option to explore Cozy Doubling as a guest or sign up with email and password so that I can start using the app without a third-party social account.

### Business Rules & Constraints
- **Self-Serve Email Registration:**
  - Will provide an in-app registration form requiring email, password, and confirmation.
  - Generates initial profile and unique player tag identical to Google sign-in ([AUTH-03]).
- **Guest Access Mode:**
  - Allows immediate access to core focus features with temporary local session credentials.
  - Open Design Question: How guest moderation, blocking, and reporting will function (e.g., whether guest device fingerprints or ephemeral IDs are used) remains under review before implementation.

### Acceptance Criteria
- [ ] *(To be finalized upon technical design approval)*

### Open Questions
- Finalize guest moderation architecture: How should abuse prevention, user blocking, and reporting work for users without persistent account identities?

---

## [AUTH-03] Automatic Profile & Player Tag Initialization
**Priority:** High

### Description
As a newly registered user, I want my profile and identity to be automatically set up upon first sign-in so that I can immediately start focusing and connecting with friends.

### Business Rules & Constraints
- Profile creation occurs automatically and atomically upon account creation.
- Initial profile field values must be:
  - `display_name`: Extracted from the sign-in provider (e.g., Google full name); if blank or unavailable, defaults to `"Cozy Doubler"`.
  - `leaves`: Initial balance of `0`.
  - `total_minutes_focused`: Initial count of `0`.
  - `is_supporter`: Defaults to `false`.
  - `equipped_theme_id`: Defaults to `null` (system default theme).
- **Player Tag Constraints:**
  - Must be exactly 8 uppercase alphanumeric characters.
  - Allowed characters: `23456789ABCDEFGHJKLMNPQRSTUVWXYZ` (ambiguous characters `0`, `1`, `I`, `O` are excluded to avoid confusion when sharing).
  - Must be globally unique across all players. Collision-retry loop must ensure uniqueness.
  - Player tags are immutable once assigned and cannot be modified by the user.

### Acceptance Criteria
- [ ] Given a new user account creation, when the account is created, then a profile record is created with 0 leaves, 0 focus minutes, and `is_supporter = false`.
- [ ] Given a new user with Google name "Sam Cozy", when the profile is generated, then `display_name` is set to "Sam Cozy".
- [ ] Given a new user with no name provided, when the profile is generated, then `display_name` falls back to `"Cozy Doubler"`.
- [ ] Given a generated player tag, then it contains exactly 8 characters from the allowed charset and never contains `0`, `1`, `I`, or `O`.
- [ ] Given an existing tag collision in the generation loop, then the system regenerates until a unique tag is secured before saving.

### Open Questions
- None.

---

## [AUTH-04] Session Persistence & Automatic Re-authentication
**Priority:** High

### Description
As a returning user, I want my session to stay logged in across app restarts so that I can jump into focus rooms without signing in every time.

### Business Rules & Constraints
- An authenticated session remains valid across app kills and device reboots.
- During app startup, the app validates session state and navigates directly to the Home screen if valid, preventing screen flashing.
- A 1-second debounce is applied on unauthenticated state transitions before redirecting to Login to prevent premature logouts during token refresh or brief connectivity transitions.
- If a session is definitively revoked or expired on the server, the user must be redirected to the Login screen.

### Acceptance Criteria
- [ ] Given a user with a valid stored session, when the app is launched, then the Home screen loads directly and the Login screen is never shown.
- [ ] Given a user in an active session, when credentials undergo background rotation, then active focus timers and task states continue undisturbed.
- [ ] Given a session that has been revoked on the server, when verified, then after a 1-second debounce the app navigates to the Login screen and clears local profile state.
- [ ] Given a brief network drop while using the app, then the session is retained and an offline banner/dialog is presented instead of logging out.

### Open Questions
- None.

---

## [AUTH-05] Update Display Name
**Priority:** Medium

### Description
As a user, I want to edit my display name in Settings so that my friends and co-working participants see my preferred name.

### Business Rules & Constraints
- Only the `display_name` field may be updated by this action.
- The update must not allow changing `player_tag`, `leaves`, `total_minutes_focused`, or `is_supporter`.
- Blank or whitespace-only names must be rejected.
- Updating the display name immediately refreshes the active profile state across the app (Settings, Home, Focus Room, Friends).

### Acceptance Criteria
- [ ] Given an authenticated user, when they enter a non-blank name `"Flora"` and submit, then their display name updates in the system.
- [ ] Given an updated display name, when the user navigates through the app, then all screens immediately display `"Flora"`.
- [ ] Given an empty string or whitespace-only input, when submitted, then the update is rejected and the existing name is preserved.
- [ ] Given an update request, the system strictly disallows modifying `leaves` or `player_tag`.

### Open Questions
- Is there a defined maximum character limit for `display_name`? (Currently unrestricted in database, UI suggests single-line text field).

---

## [AUTH-06] Sign Out & Device Data Sanitization
**Priority:** High

### Description
As a user, I want to sign out from Settings so that my account is disconnected from the device and another person using the device cannot see my private information.

### Business Rules & Constraints
- Signing out immediately terminates the active session on the device and invalidates it on the server.
- **Privacy Sanitization Rules:**
  1. The push notification device token (FCM) must be immediately cleared on the server to prevent future friend notifications from being delivered to this device.
  2. Locally cached unfinished focus tasks must be deleted from device storage.
  3. Cached in-memory profile state, friends lists, and inventory must be cleared.
- The navigation back stack must be completely cleared so pressing the device Back button cannot return to authenticated screens.

### Acceptance Criteria
- [ ] Given a logged-in user, when they tap "Sign Out" in Settings, then the server session is terminated, local tasks are wiped, and the user is redirected to the Login screen.
- [ ] Given a user has signed out, when the device Back button is pressed on the Login screen, then the app exits rather than navigating back into the app.
- [ ] Given user A signed out and user B signs in on the same device, then user B sees zero unfinished tasks from user A and receives only their own notifications.
- [ ] Given an offline device, when "Sign Out" is tapped, then local credentials and caches are purged immediately and the Login screen is shown.

### Open Questions
- None.

---

## [AUTH-07] Permanent Account Deletion
**Priority:** High

### Description
As a user, I want to permanently delete my account and data from Settings so that my personal information is completely removed in accordance with Google Play privacy requirements.

### Business Rules & Constraints
- Account deletion is a destructive, irreversible action requiring user confirmation.
- **Ordered Data Teardown:**
  1. All records in user inventory are deleted.
  2. All friendships and friend requests involving the user (as sender or receiver) are deleted.
  3. All focus sessions associated with the user are deleted.
  4. The user profile is deleted.
  5. The underlying authentication record is deleted.
- Blocks and reports referencing the user must cascade cleanly without orphan records.
- After server-side deletion, the app executes the local sign-out and cleanup sequence ([AUTH-06]).

### Acceptance Criteria
- [ ] Given an authenticated user, when they confirm account deletion in Settings, then all inventory items, friendships, focus sessions, and profile records are deleted.
- [ ] Given successful account deletion, then local session credentials, local task caches, and push tokens are wiped and the user is placed on the Login screen.
- [ ] Given a deleted user account, when attempting to sign in again with the old credentials, then the account is treated as non-existent.
- [ ] Given existing friends of the deleted user, when they view their Friends list, then the deleted user no longer appears.

### Open Questions
- When a user is deleted, should their submitted violation reports be retained anonymously for moderation audit? (Database schema currently sets `reporter_id = null` on delete, which preserves the report reason).

---
---

# Epic: Live Focus Room & Body Doubling

**Goal:** Provide an engaging, real-time virtual body-doubling room where users can focus on their personal tasks alongside other active participants, broadcast live progress, and stay motivated.  
**Scope:**
- Focus Session Initialization
- Task CRUD (Add, Toggle Status, Delete, Active Task Selection)
- Local Offline Task Persistence & Recovery
- Live Room Presence and Real-Time Task Broadcasting
- Throttled Participant Updates & Presence Tracking
- Friend Notification Trigger on Entry

---

## [ROOM-01] Enter Focus Room & Start Session
**Priority:** High

### Description
As a user, I want to enter the Focus Room from the Home screen so that a new tracked focus session begins and I can start working.

### Business Rules & Constraints
- Entering the room initiates a focus session with `start_time = now()`, `is_processed = false`, and initial zeroed counters (`tasks_completed = 0`, `duration_minutes = 0`, `earned_leaves = 0`).
- If a session is already active (e.g., returning from an app process restoration), the existing session ID must be restored rather than starting a duplicate.
- Entering the room automatically triggers push notifications to all accepted friends (see [SOC-05]).
- The participant joins the real-time room channel using a unique identifier composed of `user_id:session_id` to prevent presence collisions upon rapid re-entry.

### Acceptance Criteria
- [ ] Given the user is on the Home screen, when they tap "Start Focus", then a focus session is created with the current timestamp and they are navigated to the Focus Room.
- [ ] Given the room is opened, then the user connects to the live focus room channel and announces their presence.
- [ ] Given the user has accepted friends with registered push tokens, when the session starts, then a notification is dispatched to those friends.
- [ ] Given an existing session ID preserved in navigation state, when the screen is reconstructed, then the existing session is resumed without creating a new session.

### Open Questions
- None.

---

## [ROOM-02] Manage Session Tasks
**Priority:** High

### Description
As a user in the Focus Room, I want to add, select, complete, and delete tasks so that I can track what I am accomplishing during my focus period.

### Business Rules & Constraints
- **Adding Tasks:**
  - Tasks require a non-blank text description.
  - Adding a task when no active task is selected automatically designates the new task as the active task.
  - New tasks default to `is_completed = false`.
- **Selecting Active Task:**
  - Tapping any task designates it as the currently active task.
  - The active task's text is broadcast to other participants in the room.
- **Completing Tasks:**
  - Toggling a task updates its completion status between finished and unfinished.
  - Completed task count directly impacts the session's reward formula at completion.
- **Deleting Tasks:**
  - Only unfinished tasks can be deleted. Once a task is completed, it cannot be deleted (preserves earned reward accountability).
  - If the active task is deleted, the active selection falls back to the next remaining task, or `null` if none remain.
- Every task mutation immediately triggers a real-time broadcast to room participants and updates local persistent storage.

### Acceptance Criteria
- [ ] Given the user enters text `"Read Chapter 4"` and adds it, then the task appears in the task list.
- [ ] Given a user with no active task, when they add their first task, then that task is automatically set as active.
- [ ] Given an active task, when the user taps a different task in the list, then the active selection changes to that task.
- [ ] Given an unfinished task, when the user checks its checkbox, then its status becomes completed.
- [ ] Given a completed task, when viewing the task options, then the delete button is disabled or unavailable.
- [ ] Given an unfinished active task, when the user deletes it, then the task is removed from the list and the active selection shifts to the first available task.

### Open Questions
- None.

---

## [ROOM-03] Local Task Persistence & Cache Recovery
**Priority:** Medium

### Description
As a user, I want my unfinished tasks to be saved locally so that if I exit the room or restart the app, my to-do items are not lost.

### Business Rules & Constraints
- Unfinished tasks are automatically synchronized to local device storage whenever tasks are added, modified, or deleted.
- Completed tasks are excluded from the local persistent cache (they are finalized upon session completion).
- When entering a new focus session, any saved unfinished tasks from previous sessions are automatically restored into the task list.
- If the user explicitly signs out or deletes their account, the local task cache must be wiped immediately ([AUTH-06]).

### Acceptance Criteria
- [ ] Given 3 tasks where 1 is completed and 2 are unfinished, when saved, then only the 2 unfinished tasks are written to local persistent storage.
- [ ] Given the user closes the app while having unfinished tasks, when they reopen the app and start a focus session, then those unfinished tasks are restored in the task list.
- [ ] Given the user completes all tasks in a session and finishes, then the local task cache is empty for the next session.
- [ ] Given the user signs out, when checked, the local task storage is completely cleared.

### Open Questions
- None.

---

## [ROOM-04] Real-Time Participant Presence & Status Broadcasting
**Priority:** High

### Description
As a user in the Focus Room, I want to see other participants and what they are working on in real time so that I feel the accountability and energy of a live study room.

### Business Rules & Constraints
- Participants in the room share:
  - Display name
  - Active task description (or `"No active task"` if none selected)
  - Completed task count
  - Total task count
- **Dual-Path Synchronization:**
  1. *Instant Broadcast:* Direct peer-to-peer real-time broadcast delivers instant task text and toggle changes to active participants.
  2. *Throttled Presence Tracking:* Persistent presence updates are throttled to a minimum interval of 10 seconds to prevent channel flooding.
- The user's own card is excluded from the list of other participants.
- Participants on the user's blocked list must be filtered out immediately and must never appear in the room ([SAFE-02]).
- When a participant leaves or disconnects, their card is removed from the list.
- **Room Architecture & Hard Capacity Limit:**
  - The application operates with a single global focus room. Private rooms with custom titles are planned for future releases.
  - A hard limit of **1,000 concurrent participants** is enforced to mitigate cyber attacks, denial-of-service, and WebSocket connection flooding.

### Acceptance Criteria
- [ ] Given another user joins the room, when their presence is detected, then an avatar card showing their name, active task text, and task count appears in the UI.
- [ ] Given a remote participant changes their active task or marks a task complete, then their card updates in real time.
- [ ] Given a remote participant is on the current user's blocked list, then their presence is hidden and not displayed in the room.
- [ ] Given the user leaves the room, then their presence is removed from other participants' screens within seconds.
- [ ] Given the global focus room reaches its 1,000 active participant capacity ceiling, then additional join requests are rejected gracefully with a capacity notice.

### Open Questions
- None.

---

## [ROOM-05] Notify Friends Upon Entering Room
**Priority:** Medium

### Description
As a focusing user, I want my friends to be notified when I enter a focus room so that they know I am studying and can join me.

### Business Rules & Constraints
- Notification is dispatched when `startSession` is called upon room entry.
- Only friends with `accepted` friendship status are notified.
- Friends who do not have an active push token registered are skipped.
- Notification content format:
  - Title: `"Friend Focusing!"`
  - Body: `"[Display Name] just entered the focus room."` (Falls back to `"A friend just entered the focus room."` if name is unavailable).
- Users who have blocked the entering player or are blocked by the entering player must not receive the notification.
- **Future Roadmap & Mute Configuration:**
  - A user-facing setting in Settings will be added to allow users to toggle/mute friend room entry notifications.
  - Planned future behavior will transition away from automated broadcast-on-entry to manual, targeted nudging or inviting specific friends directly from the Friends menu.

### Acceptance Criteria
- [ ] Given user A starts a session and has accepted friend user B with a valid push token, when user A enters the room, then user B receives the `"Friend Focusing!"` notification.
- [ ] Given user A and user C have a pending (unaccepted) friendship request, when user A enters the room, then user C receives no notification.
- [ ] Given user A and user D are mutual friends but user D has blocked user A, then user D receives no notification.

### Open Questions
- None.

---
---

# Epic: Session Completion & Leaf Economy

**Goal:** Reward users for productive focus time by accurately computing and granting Leaf currency, updating verified focus statistics, and displaying an accomplishment summary.  
**Scope:**
- Finish Focus Session Action
- Server-Derived Leaf & Duration Calculation Formula
- Account Balance & Focus Milestone Updates
- Post-Session Summary Display
- Anti-Cheat & Single-Processing Enforcement

---

## [ECON-01] End Focus Session & Server-Side Verification
**Priority:** High

### Description
As a user who has finished working, I want to end my focus session so that my time and completed tasks are tallied and converted into Leaves.

### Business Rules & Constraints
- Tapping "Finish Work" leaves the real-time room channel, finalizes the session, and triggers reward calculation.
- **Strict Server-Side Duration Derivation:**
  - Session duration in minutes is computed exclusively on the server as `(now() - start_time) / 60`.
  - The client provides only `session_id`, `tasks_completed` count, and `last_task_text`.
  - The client has no control over duration, preventing clock manipulation on the device.
- A session cannot be finalized if `is_processed` is already `true`.
- Finalizing a session records `end_time = now()`, `duration_minutes`, `tasks_completed`, `earned_leaves`, `last_task_text`, and sets `is_processed = true`.

### Acceptance Criteria
- [ ] Given an active focus session, when the user taps "Finish Work", then the session is marked processed with the server's current timestamp as `end_time`.
- [ ] Given a session started 25 minutes ago according to server time, when finalized, then `duration_minutes` is recorded as 25 regardless of device clock settings.
- [ ] Given a session has already been finalized, when another finish request is received for the same session ID, then the request is rejected with `"Session not found or already processed."`
- [ ] Given successful session completion, then the app transitions immediately to the Summary screen passing the session ID.

### Open Questions
- None.

---

## [ECON-02] Leaf & Minutes Calculation Formula
**Priority:** High

### Description
As a user, I want to earn Leaves and accumulate focus minutes based on my focus duration and task completions so that I can buy shop items and track my milestone growth.

### Business Rules & Constraints
- **Reward Formula:**
  $$\text{earned\_leaves} = (\text{duration\_minutes} \times 1) + (\text{tasks\_completed} \times 3)$$
- **Minimum Reward Floor:**
  - If `earned_leaves < 1`, `earned_leaves` is set to `1` (ensures even brief or 0-task test sessions grant at least 1 Leaf).
- **Profile Statistics Update:**
  - The user's profile balance is atomically incremented: `leaves = leaves + earned_leaves`.
  - The user's lifetime focus counter is atomically incremented: `total_minutes_focused = total_minutes_focused + duration_minutes`.
- Balance increments are performed exclusively on the server and cannot be directly written by the client.

### Acceptance Criteria
- [ ] Given a 30-minute session with 3 completed tasks, when finalized, then the earned leaves equal $(30 \times 1) + (3 \times 3) = 39$ leaves.
- [ ] Given a 0-minute session with 0 completed tasks, when finalized, then the earned leaves equal 1 leaf (minimum floor).
- [ ] Given a user with an initial balance of 50 leaves and 100 focus minutes, when finishing a 20-minute session with 2 tasks ($20 + 6 = 26$ leaves), then their new balance is 76 leaves and total focus minutes is 120.

### Open Questions
- Is there a maximum cap on focus duration per session (e.g., maximum 8 hours or 480 minutes) to prevent runaway leaf accumulation if a user forgets to close the app? (Currently unbounded in schema).

---

## [ECON-03] Post-Session Summary Screen
**Priority:** High

### Description
As a user who just finished a session, I want to see a celebration summary of my minutes, tasks, and leaves earned so that I feel a sense of completion.

### Business Rules & Constraints
- The Summary screen loads the finalized session data using the passed `sessionId`.
- Displays:
  - Focused Minutes (`duration_minutes`)
  - Tasks Finished (`tasks_completed`)
  - Leaves Earned (`earned_leaves`)
- Tapping "Continue" navigates back to the Home screen and clears the Focus Room from the back stack so the user cannot navigate back into the finished room.

### Acceptance Criteria
- [ ] Given a finished session with 45 minutes, 4 tasks, and 57 leaves, when the Summary screen opens, then all three values match the recorded session data.
- [ ] Given the user taps "Continue" on the Summary screen, then they are returned to the Home screen.
- [ ] Given the user is on the Home screen after viewing the Summary, when pressing device Back, then the app exits rather than reopening the finished Focus Room.

### Open Questions
- None.

---
---

# Epic: Friends & Social Activity

**Goal:** Help users stay connected and motivated with study buddies by exchanging player tags, tracking friend activity, and observing friends' focus accomplishments.  
**Scope:**
- Search Player by Unique Tag
- Send & Manage Friend Requests
- Mutual Request Auto-Acceptance
- View Friends List with Recent Session Stories
- Push Notifications on Friend Room Entry

---

## [SOC-01] Search Players by Tag & Send Friend Request
**Priority:** High

### Description
As a user, I want to search for another player using their 8-character player tag and send a friend request so that we can become focus buddies.

### Business Rules & Constraints
- Search is performed against `player_tag` (case-insensitive, optional leading `#` ignored).
- **Validation Rules:**
  - Users cannot send a friend request to themselves. Attempting to add one's own tag returns `"You can't add yourself!"`.
  - If the player tag does not exist, the system returns `"User not found"`.
- **Relationship Uniqueness & Canonical Ordering:**
  - A relationship row is uniquely identified by the pair `(user_id_1, user_id_2)` where `user_id_1 < user_id_2`.
  - If a friendship already exists with status `accepted`, the request fails with `"Already friends!"`.
  - If the current user has already sent a pending request, the request fails with `"Request already sent!"`.
  - A new friendship record is inserted with `status = 'pending'` and `requester_id = current_user.id`.

### Acceptance Criteria
- [ ] Given player tag `"k7m2p9x4"` or `"#K7M2P9X4"`, when searched, then the target user profile is correctly matched.
- [ ] Given a user enters their own player tag, when submitting, then the request is blocked with `"You can't add yourself!"`.
- [ ] Given a non-existent tag `"ZZZZZZZZ"`, when submitting, then the system returns `"User not found"`.
- [ ] Given an existing pending request already sent by the user, when attempting to send again, then `"Request already sent!"` is returned.
- [ ] Given a valid, unlinked player tag, when submitted, then a pending friendship is created and the UI notifies `"Friend request sent!"`.

### Open Questions
- None.

---

## [SOC-02] Automatic Mutual Request Acceptance
**Priority:** Medium

### Description
As a user whose friend already sent me a request, I want the system to automatically accept our friendship if I send a request back to them, so that we don't have duplicate pending states.

### Business Rules & Constraints
- When user A attempts to send a friend request to user B:
  - If a pending request already exists where `requester_id == user_B.id`, the system treats user A's action as an acceptance.
  - The status is updated to `accepted`, and `accepted_at` is set to the current timestamp.
  - Both users become friends immediately without requiring user A to find the request in the incoming requests list.

### Acceptance Criteria
- [ ] Given user B has an outstanding pending request sent to user A, when user A attempts to send a request to user B's player tag, then the friendship is immediately marked `accepted`.
- [ ] Given mutual auto-acceptance occurs, then both user A and user B see each other in their Friends list immediately.

### Open Questions
- None.

---

## [SOC-03] Accept or Decline Pending Friend Requests
**Priority:** High

### Description
As a user with incoming friend requests, I want to review, accept, or decline pending requests so that I control who can view my focus activity.

### Business Rules & Constraints
- **Viewing Requests:**
  - The incoming requests list displays requests where `status == 'pending'` and `requester_id != current_user.id`.
  - Requests from blocked users are excluded ([SAFE-02]).
- **Accept Request:**
  - Updates the friendship status to `accepted` and sets `accepted_at = now()`.
  - Both players are now friends and can view each other's focus stories.
- **Decline Request:**
  - Deletes the pending friendship row from the database.
  - The sender is not explicitly notified of the decline; the row is simply removed.

### Acceptance Criteria
- [ ] Given an incoming request from user "Leo", when the user taps "Accept", then the request moves to the active Friends list with status `accepted`.
- [ ] Given an incoming request from user "Leo", when the user taps "Decline", then the request is removed from the pending list and deleted.
- [ ] Given an accepted friendship, then both users start receiving room entry push notifications for each other.

### Open Questions
- None. (Unfriending is documented in [SOC-05]).

---

## [SOC-04] View Friends List with Recent Session Stories
**Priority:** High

### Description
As a user, I want to view my list of friends along with their most recent focus achievements so that I can see when they last focused and celebrate their progress.

### Business Rules & Constraints
- Only friendships with `status == 'accepted'` are displayed.
- For each friend, the list displays:
  - Display name and player tag
  - Duration of their most recent completed session in minutes (if available)
  - Date/time of their most recent completed session (if available)
  - The task description of their last completed task (if available)
  - Date they became friends (`accepted_at`)
- If a friend has not yet completed any sessions, their card displays gracefully without session statistics (fields are nullable).
- Friends on the user's blocked list are automatically hidden from this list.

### Acceptance Criteria
- [ ] Given friend "Daisy" whose last session was 45 minutes on "Math Homework", then Daisy's card displays her name, tag, 45m duration, and "Math Homework".
- [ ] Given a newly added friend who has never run a focus session, then their card displays their name and tag without crashing or showing error text.
- [ ] Given a friend is added to the user's blocked list, then that friend vanishes from the Friends list immediately.

### Open Questions
- None.

---

## [SOC-05] Remove / Unfriend an Existing Friend
**Priority:** Medium

### Description
As a user, I want to remove an existing friend from my Friends list so that we no longer share focus activity or receive each other's focus notifications.

### Business Rules & Constraints
- An accepted friend can be removed directly from the Friends list options.
- Unfriending deletes the underlying relationship record `(user_id_1, user_id_2)`.
- Upon deletion:
  - Both users are immediately removed from each other's Friends lists.
  - Room entry push notifications are no longer dispatched between the two users ([ROOM-05]).
  - Either user may send a new friend request in the future using the standard player tag flow.

### Acceptance Criteria
- [ ] Given user A and user B are accepted friends, when user A taps "Unfriend" on user B's card, then the friendship record is deleted.
- [ ] Given user A unfriends user B, then user B immediately disappears from user A's Friends list, and user A immediately disappears from user B's Friends list.
- [ ] Given user A unfriends user B, when user A enters a focus room, then user B receives no push notification.

### Open Questions
- None.

---
---

# Epic: Shop & Supporter Subscriptions

**Goal:** Offer a monetized economy where users can purchase aesthetic themes using earned Leaves or subscribe to a Supporter Pass to unlock premium perks.  
**Scope:**
- Browse Theme & Supporter Pass Catalog
- Purchase Themes with In-App Leaf Currency
- Purchase Supporter Pass Subscriptions (Monthly, Yearly, Lifetime)
- Real-Time Subscription Status Evaluation
- Restore Purchases
- External Subscription Management

---

## [SHOP-01] Browse Shop Catalog
**Priority:** High

### Description
As a user, I want to browse the Oasis Shop to explore available themes and supporter passes so that I can decide how to customize my experience.

### Business Rules & Constraints
- Items in the shop are categorized as:
  - **Themes:** Visual color palettes. Can be either standard (purchasable with Leaves) or premium (Supporter Pass only).
  - **Supporter Passes:** Real-money subscription products (e.g., Monthly, Yearly, Lifetime) that unlock all premium themes.
- Each theme card displays its full Material color palette preview (primary, container, background, surface, etc.).
- Item ownership status is evaluated per user:
  - If owned via Leaves: Displays as owned.
  - If premium and user has active Supporter Pass: Treated as owned/unlocked.
  - If equipped: Badged as currently equipped.
- Supporter passes display real localized pricing retrieved from Google Play Billing.

### Acceptance Criteria
- [ ] Given the user opens the Shop tab, then all catalog themes and supporter pass tiers are displayed.
- [ ] Given a theme item, its color palette preview renders the identical color values used when equipped in the app.
- [ ] Given a user who already owns a theme, the card indicates "Owned" instead of showing a purchase price.
- [ ] Given an active supporter viewing premium themes, the themes indicate unlocked status.

### Open Questions
- None.

---

## [SHOP-02] Purchase Themes with Earned Leaves
**Priority:** High

### Description
As a user with accumulated Leaves, I want to buy non-premium themes from the Shop so that I can expand my theme collection.

### Business Rules & Constraints
- **Eligibility Checks:**
  1. The item must NOT be premium (`is_premium == false`). Premium items cannot be purchased with Leaves under any circumstance and return `"Premium items are unlocked via a Supporter Pass and cannot be bought with leaves."`
  2. The user must not already own the item. Attempting to repurchase returns `"You already own this item."`
  3. The user must have a Leaf balance greater than or equal to `leaf_price`. Insufficient balance returns `"Not enough leaves."`
- **Atomic Transaction:**
  - If all checks pass, the item price is deducted from `profiles.leaves`, and a row is inserted into `user_inventory`.
  - Both changes are committed in a single atomic transaction.
- Once purchased, the theme appears immediately in the user's Inventory ([THEME-01]).

### Acceptance Criteria
- [ ] Given a theme costing 100 leaves and a user with 150 leaves, when purchased, then 100 leaves are deducted, the theme is added to inventory, and the new balance is 50 leaves.
- [ ] Given a theme costing 100 leaves and a user with 60 leaves, when purchase is attempted, then the transaction fails with `"Not enough leaves"` and the balance remains 60.
- [ ] Given a premium theme, when a user attempts to buy it with leaves, then the transaction fails with `"Premium items are unlocked via a Supporter Pass..."`.
- [ ] Given an already owned theme, when purchase is attempted, then the transaction fails with `"You already own this item."`.

### Open Questions
- None.

---

## [SHOP-03] Purchase Supporter Pass Subscription
**Priority:** High

### Description
As a user, I want to subscribe to a Supporter Pass using Google Play Billing so that I can unlock all premium themes and support app development.

### Business Rules & Constraints
- Purchasing a Supporter Pass initiates the Google Play Billing flow for the selected package (Monthly, Yearly, or Lifetime).
- Upon successful payment:
  - The entitlement `"Cozy Doubling Supporter Pass"` becomes active.
  - The user's profile is updated with `is_supporter = true`.
  - All premium themes become instantly available for equipping in Inventory.
- If a subscription expires or is revoked:
  - `is_supporter` is set to `false`.
  - **Auto-Revert on Expiration:** If the user currently has a premium theme equipped, the system must automatically unequip the theme and revert `equipped_theme_id` to `null` (system default theme) upon the next profile sync.

### Acceptance Criteria
- [ ] Given a free user, when they complete a Supporter Pass purchase, then their profile status changes to `is_supporter = true`.
- [ ] Given an active supporter, then all premium themes in the Shop and Inventory indicate unlocked status.
- [ ] Given a purchase attempt that is cancelled by the user in the Google Play dialog, then no charges occur and `is_supporter` remains `false`.
- [ ] Given a user with a premium theme equipped whose subscription expires, when profile status syncs, then `is_supporter` becomes `false` and the equipped theme automatically reverts to the System Default.

### Open Questions
- None.

---

## [SHOP-04] Restore Previous In-App Purchases
**Priority:** Medium

### Description
As a returning user or user on a new device, I want to restore my purchases from Settings so that my active Supporter status is recognized without paying again.

### Business Rules & Constraints
- Tapping "Restore Purchases" in Settings checks Google Play for active subscription entitlements under the user's Google Play account.
- If an active entitlement for `"Cozy Doubling Supporter Pass"` is found:
  - A backend synchronization is triggered to verify and link the active entitlement to the user's profile.
  - `profiles.is_supporter` is set to `true`.
  - The UI displays `"Purchases restored successfully!"`.
- If no active entitlement is found, the UI displays `"No active purchases found to restore."`.

### Acceptance Criteria
- [ ] Given a user with an active subscription on Google Play, when they tap "Restore Purchases", then `is_supporter` updates to `true` and a success confirmation is shown.
- [ ] Given a user with no active subscriptions on Google Play, when they tap "Restore Purchases", then the UI notifies `"No active purchases found to restore."`.

### Open Questions
- None.

---

## [SHOP-05] Manage Subscription Deep-Linking
**Priority:** Low

### Description
As a supporter, I want a direct link to manage or cancel my subscription in Google Play from Settings so that I have full control over my recurring payments.

### Business Rules & Constraints
- Settings provides a "Manage Subscription" option for supporters.
- Tapping opens the Google Play Store subscription management page targeted directly to this app's package (`https://play.google.com/store/account/subscriptions?package=[package_name]`).
- If the Play Store app is not installed, it falls back to opening the URL in an external web browser.

### Acceptance Criteria
- [ ] Given a user taps "Manage Subscription", then the device launches Google Play directly to the subscription management page for Cozy Doubling.
- [ ] Given Google Play is disabled or unavailable, then the fallback browser opens the Play Store subscriptions URL.

### Open Questions
- None.

---
---

# Epic: Inventory & Theme Customization

**Goal:** Allow users to manage their acquired themes, preview cohesive color schemes, and customize the visual aesthetic of the entire application.  
**Scope:**
- Owned & Unlocked Theme List
- Theme Preview & Color Schemes
- Dynamic Theme Equipping
- Resetting to System Default Theme
- Sticky Theme Caching & Flicker Prevention

---

## [THEME-01] View Owned and Supporter-Unlocked Themes
**Priority:** High

### Description
As a user in the Oasis Inventory, I want to view all the themes I own or have unlocked so that I can choose which look to apply to my app.

### Business Rules & Constraints
- The Inventory list includes:
  1. **System Default Theme:** Always owned, price 0, cannot be deleted.
  2. **Purchased Themes:** Themes present in `user_inventory` for this user.
  3. **Supporter Themes:** Premium themes (`is_premium == true`) if the user has an active Supporter Pass (`is_supporter == true`).
- If a user is not a supporter, unpurchased premium themes do not appear in the owned inventory list.
- The currently equipped theme is distinctly marked with an "Equipped" indicator.

### Acceptance Criteria
- [ ] Given any user, the "System Default" theme is always visible and available in the inventory.
- [ ] Given a user who bought "Cozy Moss" with leaves, "Cozy Moss" appears in their owned inventory list.
- [ ] Given an active supporter, all premium themes appear in their owned inventory list.
- [ ] Given a non-supporter who has not purchased premium themes, premium themes do not appear in their owned inventory list.

### Open Questions
- None.

---

## [THEME-02] Equip Theme Palette & App-Wide Dynamic Theming
**Priority:** High

### Description
As a user, I want to equip an owned theme so that the entire app's color palette (buttons, backgrounds, avatars, and cards) changes to that theme's aesthetic.

### Business Rules & Constraints
- **Eligibility Validation:**
  - The item must exist and belong to `category_id = 'theme'`.
  - The user must either own the item in `user_inventory` OR (the item is premium AND `user.is_supporter == true`).
  - If validation fails, the action is rejected with `"You do not own this item or have an active Supporter Pass."`
- **Application:**
  - Upon equipping, `profiles.equipped_theme_id` is updated to the item's ID.
  - The app dynamically updates its 18 Material 3 color roles (primary, on-primary, primary-container, background, surface, etc.) to match the palette stored in `theme_details`.
  - All screens in the application immediately update without requiring an app restart.
  - Destructive action colors (such as "Delete Account" red) intentionally remain fixed and are not recolored by theme palettes.
- **Lapsed Supporter Auto-Reversion:**
  - If a user has a premium theme equipped and their Supporter Pass expires or is cancelled, the system automatically clears `equipped_theme_id` to `null` and resets the theme to System Default ([SHOP-03]).

### Acceptance Criteria
- [ ] Given an owned theme, when the user taps "Equip", then `equipped_theme_id` is updated and the theme becomes active.
- [ ] Given a theme not owned by a free user, when equipping is attempted, then the action is rejected.
- [ ] Given a newly equipped theme, then buttons, top bars, bottom bars, and card containers throughout the app immediately adopt the new color scheme.
- [ ] Given an equipped premium theme, when the user's Supporter Pass lapses, then upon next session check the theme automatically resets to System Default.

### Open Questions
- None.

---

## [THEME-03] Unequip Theme & Revert to System Default
**Priority:** Medium

### Description
As a user with a custom theme equipped, I want to unequip it so that the app returns to its original warm cream and sage system default colors.

### Business Rules & Constraints
- Tapping "Equip" on the "System Default" item calls the unequip action.
- Sets `profiles.equipped_theme_id = null`.
- The app immediately reverts its theme palette to `CozyPalettes.SystemDefault`.

### Acceptance Criteria
- [ ] Given a custom theme currently equipped, when the user selects "System Default", then `equipped_theme_id` becomes `null`.
- [ ] Given `equipped_theme_id` is `null`, then the app renders using the default brand colors (cream background and sage primary).

### Open Questions
- None.

---

## [THEME-04] Sticky Theme Caching to Prevent Screen Flickers
**Priority:** Medium

### Description
As a user navigating between screens or resuming from background, I want the theme colors to stay stable so that the app does not flash between default and custom colors.

### Business Rules & Constraints
- The app maintains an in-memory sticky cache of the last loaded `ThemePalette`.
- During momentary lifecycle events, network revalidation, or session refresh, the UI retains the sticky palette rather than falling back to a loading or default theme state.
- If a new theme is explicitly equipped, the sticky cache updates immediately.

### Acceptance Criteria
- [ ] Given a user with a custom theme, when switching between main tabs or backgrounding the app, then the screen never flickers to the default theme.
- [ ] Given an initial cold launch, the app displays a solid brand splash until both the auth session and theme palette are resolved.

### Open Questions
- None.

---
---

# Epic: User Safety & Moderation

**Goal:** Protect users from harassment and inappropriate conduct by providing instant blocking and formal violation reporting tools.  
**Scope:**
- Block User Action (from Room or Friends)
- Real-Time Block Synchronization & Filtering
- Report User Conduct Violation

---

## [SAFE-01] Block User from Focus Room or Friends List
**Priority:** High

### Description
As a user, I want to block another user if they behave inappropriately so that they cannot interact with me or see me in focus rooms.

### Business Rules & Constraints
- A user can block another player directly from:
  - The Focus Room participant card options
  - The Friends list or incoming request cards
- Blocking creates a block record: `(blocker_id, blocked_id)`.
- If the blocked user was an accepted friend, the active block overrides and hides the relationship.
- Blocking is idempotent (blocking an already-blocked user performs no duplicate insert).

### Acceptance Criteria
- [ ] Given user A taps "Block" on participant user B in the Focus Room, then user B is recorded as blocked by user A.
- [ ] Given user A blocks user B, then user B's participant card immediately disappears from user A's Focus Room.
- [ ] Given user A blocks user B who is an existing friend, then user B no longer appears in user A's active Friends list.

### Open Questions
- Is there an unblock management UI currently exposed in Settings, or can users only unblock via database/support? (The repository supports unblocking via delete on `user_blocks`, but check if a "Blocked Users" screen exists in Settings).

---

## [SAFE-02] Real-Time Filtering of Blocked Users
**Priority:** High

### Description
As a user, I want blocked user filtering to apply in real time so that blocked players are instantly hidden without restarting the app.

### Business Rules & Constraints
- The app maintains an active list of all blocked user IDs involving the current user (either where current user is `blocker_id` OR `blocked_id`).
- When a block occurs, a real-time event updates the active blocked user set immediately.
- **Filter Enforcements:**
  1. *Focus Room:* Participants whose user ID is in the blocked set are filtered out of the participant list.
  2. *Friends List:* Friends or incoming requests matching the blocked set are filtered out.
  3. *Push Notifications:* Notifications from blocked users are suppressed.

### Acceptance Criteria
- [ ] Given user A and user B are in the same Focus Room, when user A blocks user B, then user B disappears from user A's room within 1 second.
- [ ] Given user A has blocked user B, when user B sends a friend request, then user A never sees the request in their incoming list.
- [ ] Given user A has blocked user B, when user B enters a focus room, then user A receives no notification.

### Open Questions
- None.

---

## [SAFE-03] Report Inappropriate User Conduct
**Priority:** Medium

### Description
As a user, I want to report a player who has an offensive display name or inappropriate active task text so that administrators can review and moderate their account.

### Business Rules & Constraints
- A report can be filed directly from the Focus Room or Friends list.
- Requires:
  - `reported_id`: The ID of the offending player.
  - `reason`: The non-blank description of the violation provided by the reporter.
- The report is stored with the reporter's ID and current timestamp for administrative review.
- After submitting a report, the UI confirms with `"Report submitted. Thank you."`.

### Acceptance Criteria
- [ ] Given a user taps "Report" on a participant and provides reason `"Offensive name"`, then a report record is saved with the target ID, reason, and reporter ID.
- [ ] Given a successful report submission, then the UI displays `"Report submitted. Thank you."`.
- [ ] Given an empty report reason, then submission is prevented until text is entered.

### Open Questions
- None.

---
---

# Epic: Journey & Personal Progress

**Goal:** Give users visibility into their long-term focus habits, accumulated wealth, and milestones through an inspiring personal dashboard.  
**Scope:**
- Lifetime Focus Minutes Display
- Accumulated Leaf Balance Display
- Member Since / Account Longevity Display

---

## [JOURN-01] View Personal Journey Dashboard
**Priority:** Medium

### Description
As a user navigating to the Oasis Journey tab, I want to see my lifetime stats and member longevity so that I can feel proud of my focus consistency over time.

### Business Rules & Constraints
- The Journey tab displays:
  - **Username:** Current `display_name`.
  - **Account Longevity Bio:** Formatted as `"Cozy Doubler since [YYYY-MM]"` derived from the account's creation date.
  - **Total Leaves:** Current accumulated `leaves` balance.
  - **Total Focus Minutes:** Lifetime verified `total_minutes_focused` counter.
- Stats update automatically whenever profile sync events occur (such as completing a focus session or buying a theme).

### Acceptance Criteria
- [ ] Given a user created their account in March 2026, then the bio displays `"Cozy Doubler since 2026-03"`.
- [ ] Given a user with 250 leaves and 1,420 total focus minutes, then both figures are accurately rendered on the dashboard.
- [ ] Given the user completes a 30-minute focus session, when they return to Journey, then total focus minutes reflects the 30-minute increase.

### Open Questions
- Are there planned milestones, levels, or achievement badges tied to lifetime focus minutes in the future? (Currently simple metric displays).
