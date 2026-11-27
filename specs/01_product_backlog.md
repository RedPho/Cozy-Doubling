# Cozy Doubling — Product Backlog & Specification

> **Source of Truth:** Clean, product-level backlog describing implemented system behavior, client/backend readiness, and prioritized upcoming Product Backlog Items (PBIs).  
> **Status Legend:** `Implemented` | `Backend Ready — Client UI Needed` | `Planned`

---

# Epic: Authentication & Account Lifecycle

**Goal:** Enable users to seamlessly register, authenticate across devices, maintain persistent sessions, customize profile identity, and control their account lifecycle up to complete deletion.  
**Scope:**
- Google One-Tap/Native Authentication
- Email & Password Login and Self-Serve Registration
- Password Reset (planned)
- Migration of 1.x (Supabase) accounts (planned, release-blocking)
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
- **Credential verification (server-side):** The app obtains a Google ID token via Android Credential Manager ("Sign in with Google") and the backend verifies its signature, issuer, expiry, and that its audience is the app's OAuth *web* client ID before trusting any claim.
- **Account matching:** Accounts are matched on the stable Google account ID (`sub`), never on the email claim alone.
- **Linking to an existing email account:** If no account has that Google ID but an email/password account uses the same email, the Google identity is linked to it only when Google reports the email as verified. Otherwise sign-in is refused with `"An account with this email already exists. Please sign in with your email and password."`
- Developer mock tokens (`mock:google:…`) are accepted only by non-production backends and only sent by debug builds without a configured web client ID.

### Acceptance Criteria
- [ ] Given a user with an existing account linked to Google, when they authenticate with Google, then their active session is restored and they are routed to the Home screen.
- [ ] Given a first-time user, when they authenticate with Google, then an account and profile are created immediately and they are routed to the Home screen.
- [ ] Given invalid, expired, or cancelled Google credentials, when sign-in is attempted, then authentication is rejected, an error message is displayed, and the user remains on the Login screen.
- [ ] Given two simultaneous sign-in requests for the same new Google account, then exactly one user account is created and both requests resolve to that account.
- [ ] Given a forged or mock Google token sent to the production backend, then sign-in is rejected with `401`.
- [ ] Given a Google ID token issued for another app's client ID, then sign-in is rejected.
- [ ] Given the user dismisses the Google account picker, then no error dialog is shown and the user stays on the Login screen.

### Open Questions
- None.

---

## [AUTH-02] Sign In with Email & Password
**Priority:** High

### Description
As a user who prefers not to use Google (or whose device has no Google services), I want to sign in with an email address and password so that I can access my account.

### Business Rules & Constraints
- Email sign-in is a first-class, clearly visible option on the Login screen ("Continue with email"), not a hidden reviewer path. Google Play reviewers use the same flow with a provided demo account.
- Signing in never creates an account implicitly. An unknown email returns the generic error; account creation happens only through [AUTH-08].
- Email addresses must be handled case-insensitively and trimmed of leading/trailing whitespace. Passwords are sent exactly as typed (never trimmed).
- Passwords must be securely stored and verified.
- Generic error messaging must be returned on failure to prevent user enumeration (an external party cannot determine whether the email exists or the password was wrong).
- Repeated consecutive failed attempts must trigger temporary rate limiting to protect against brute-force attacks.

### Acceptance Criteria
- [ ] Given a registered user, when they provide their exact registered email and password, then authentication succeeds and they are navigated to the Home screen.
- [ ] Given an email entered in uppercase or with surrounding whitespace (e.g., `" USER@DOMAIN.COM "`), when submitted with the correct password, then authentication succeeds.
- [ ] Given an incorrect password for a registered email, when login is attempted, then authentication fails with `"Invalid email or password"`.
- [ ] Given an unregistered email address, when login is attempted, then authentication fails with the identical `"Invalid email or password"` message.
- [ ] Given an empty email, empty password, or malformed email format, when submitted, then client validation fails with a clear message and no login attempt is dispatched.
- [ ] Given repeated consecutive failed login attempts (5 within 15 minutes for the same email and IP), then further attempts are rate-limited for 15 minutes (`429`) and the user is shown `"Too many failed sign-in attempts. Please try again in N minute(s)."`
- [ ] Given an email that has never registered, when the user signs in, then no account is created.

### Open Questions
- None. (Registration: [AUTH-08]. Forgotten passwords: [AUTH-10].)

---

## [AUTH-08] Self-Serve Email Registration
**Priority:** High
**Status:** `Implemented`

### Description
As a new visitor without (or not wanting to use) a Google account, I want to create a Cozy Doubling account with my email and a password so that I can start focusing right away.

### Business Rules & Constraints
- The Login screen offers **Continue with Google** and **Continue with email**. The email dialog has two modes, toggled by `"New here? Create an account"` / `"Already have an account? Sign in"`.
- Registration form: email, optional display name, password, confirm password.
- **Validation (client and server):**
  - Email must be a syntactically valid address (`"Please enter a valid email address."`).
  - Password: minimum 8 characters, maximum 128 (`"Password must be at least 8 characters."`).
  - Password and confirmation must match (client only: `"Passwords do not match."`).
  - Duplicate email → `"Email already registered"`.
- Successful registration provisions the profile and player tag exactly as [AUTH-03] and signs the user in (token persisted per [AUTH-09]).
- Display name defaults to `"Cozy Doubler"` when left blank; capped at 30 characters.
- Email ownership is **not** verified yet; see Open Questions.

### Acceptance Criteria
- [ ] Given a new email and a valid matching password, when the user taps "Create account", then the account and profile are created and the Home screen opens.
- [ ] Given a password shorter than 8 characters or a mismatched confirmation, then the form shows the validation message and no request is sent.
- [ ] Given an email that is already registered, then `"Email already registered"` is shown inside the dialog and the user stays on the Login screen.
- [ ] Given a registered account, when the user later signs in with Google using the same verified email, then both methods open the same account ([AUTH-01]).

### Open Questions
- Should email ownership be verified (confirmation link) before the account can add friends or appear in rooms? Needed before [AUTH-10] can send reset emails safely; requires a transactional email provider.

---

## [AUTH-10] Password Reset ("Forgot password?")
**Priority:** High
**Status:** `Planned`

### Description
As an email/password user who forgot my password, I want to reset it from the Login screen so that I don't lose my account, streak, and leaves.

### Business Rules & Constraints
- "Forgot password?" link in the email sign-in dialog opens an email field.
- Backend `POST /api/auth/password-reset/request` always returns the same success message (no user enumeration) and, for existing email accounts, emails a single-use token valid for 30 minutes (stored hashed).
- `POST /api/auth/password-reset/confirm` with token + new password (same rules as [AUTH-08]) sets the password, invalidates the token and every existing session token for that account.
- Rate-limited per email and per IP.
- Requires a transactional email provider (e.g. Postmark, SES, Resend) and a sender domain with SPF/DKIM.

### Acceptance Criteria
- [ ] Given any email, when a reset is requested, then the UI shows `"If an account exists for this email, we've sent a reset link."`
- [ ] Given a valid reset token, when a new valid password is set, then sign-in works with the new password and fails with the old one.
- [ ] Given an expired or already-used token, then the reset is rejected.

---

## [AUTH-11] Guest Access Mode
**Priority:** Low
**Status:** `Planned`

### Description
As a new visitor, I want to try a focus session as a guest before creating an account.

### Business Rules & Constraints
- Guests get a temporary server identity with no friends, no purchases, and leaves held until they upgrade to a full account (Google or email) which keeps their progress.
- Open Design Question: how guest moderation, blocking, and reporting work for users without persistent identities (ephemeral IDs vs. device-bound identities).

### Acceptance Criteria
- [ ] *(To be finalized upon technical design approval)*

---

## [AUTH-12] Migrate 1.x (Supabase) Accounts to the New Backend
**Priority:** High — **Release blocker for 2.0 on Google Play**
**Status:** `Planned`

### Description
As an existing user of the Play Store version 1.1.0 (Supabase backend), I want to keep my profile, player tag, leaves, focus minutes, friends, themes, and Supporter status after updating to 2.0 so that the update doesn't wipe my progress.

### Business Rules & Constraints
- Export from Supabase: `auth.users` (id, email, Google identity `sub`), `profiles`, focus sessions, friendships, inventory, blocks, reports.
- Import keeps user IDs (RevenueCat `app_user_id` is the Supabase user ID; keeping it preserves subscriptions without RevenueCat aliasing).
- Google users: populate `users.google_sub` from the Supabase Google identity so the first 2.0 Google sign-in resolves to the migrated account ([AUTH-01]).
- Email/password users: Supabase password hashes are bcrypt and can be imported as-is, or users are asked to reset their password ([AUTH-10]).
- Cut-over: freeze writes on Supabase, run the import, then publish 2.0. Keep Supabase read-only until 2.0 adoption is high enough; 1.x clients should be shown a "please update" message (Play in-app update / minimum version check).
- Accounts deleted on Supabase before migration must not be resurrected.

### Acceptance Criteria
- [ ] Given a 1.1.0 user who signed in with Google, when they update to 2.0 and sign in with the same Google account, then their player tag, leaves, minutes, friends, and themes are unchanged.
- [ ] Given a 1.1.0 Supporter, when they update to 2.0, then Supporter status is still active without restoring purchases.

---

## [AUTH-03] Automatic Profile & Player Tag Initialization
**Priority:** High

### Description
As a newly registered user, I want my profile and identity to be automatically set up upon first sign-in so that I can immediately start focusing and connecting with friends.

### Business Rules & Constraints
- Profile creation occurs automatically and atomically upon account creation.
- Initial profile attributes must be:
  - Display Name: Extracted from the sign-in provider (e.g., Google full name); if blank or unavailable, defaults to `"Cozy Doubler"`.
  - Leaf Balance: Initial balance of 0 leaves.
  - Lifetime Focus Minutes: Initial count of 0 minutes.
  - Supporter Status: Free/standard user (`false`).
  - Equipped Theme: System default theme.
- **Player Tag Constraints:**
  - Must be exactly 8 uppercase alphanumeric characters.
  - Allowed characters: `23456789ABCDEFGHJKLMNPQRSTUVWXYZ` (ambiguous characters `0`, `1`, `I`, `O` are excluded to avoid confusion when sharing).
  - Must be globally unique across all players.
  - Player tags are immutable once assigned and cannot be modified by the user.

### Acceptance Criteria
- [ ] Given a new user account creation, when the account is created, then a profile is created with 0 leaves, 0 focus minutes, and free supporter status.
- [ ] Given a new user with Google name "Sam Cozy", when the profile is generated, then `display_name` is set to "Sam Cozy".
- [ ] Given a new user with no name provided, when the profile is generated, then `display_name` falls back to `"Cozy Doubler"`.
- [ ] Given a generated player tag, then it contains exactly 8 characters from the allowed charset and never contains `0`, `1`, `I`, or `O`.
- [ ] Every generated player tag is verified to be globally unique before being assigned to a user.

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
- Transient connection drops or background session renewals must not prematurely log the user out.
- If a session is definitively revoked or expired on the server, the user must be redirected to the Login screen.

### Acceptance Criteria
- [ ] Given a user with a valid stored session, when the app is launched, then the Home screen loads directly and the Login screen is never shown.
- [ ] Given a user in an active session, when session credentials refresh in the background, then active focus timers and task states continue undisturbed.
- [ ] Given a session that has been revoked or expired on the server, when confirmed, then the app navigates to the Login screen and clears local profile state.
- [ ] Given a brief network drop while using the app, then the session is retained and an offline banner/dialog is presented instead of logging out.

### Open Questions
- None.

---

## [AUTH-05] Update Display Name
**Priority:** Medium

### Description
As a user, I want to edit my display name in Settings so that my friends and co-working participants see my preferred name.

### Business Rules & Constraints
- Only the user's display name may be updated by this action.
- The update must not allow changing player tag, leaf balance, focus minutes, or supporter status.
- Blank or whitespace-only names must be rejected.
- Updating the display name immediately refreshes the active profile state across the app (Settings, Home, Focus Room, Friends).

### Acceptance Criteria
- [ ] Given an authenticated user, when they enter a non-blank name `"Flora"` and submit, then their display name updates in the system.
- [ ] Given an updated display name, when the user navigates through the app, then all screens immediately display `"Flora"`.
- [ ] Given an empty string or whitespace-only input, when submitted, then the update is rejected and the existing name is preserved.
- [ ] Given an update request, the system strictly disallows modifying leaf balance or player tag.

### Open Questions
- Resolved: Display name is capped at 30 characters across Android inputs, backend API validation, and `/ws/room` presence (`[UI-01]`).

---

## [AUTH-06] Sign Out & Device Data Sanitization
**Priority:** High

### Description
As a user, I want to sign out from Settings so that my account is disconnected from the device and another person using the device cannot see my private information.

### Business Rules & Constraints
- Signing out immediately terminates the active session on the device and invalidates it on the server.
- **Privacy Sanitization Rules:**
  1. The device's push notification registration must be immediately cleared on the server to prevent future friend notifications from being delivered to this device.
  2. Locally cached unfinished focus tasks must be deleted from device storage.
  3. Cached in-memory profile state, friends lists, and inventory must be cleared.
- Pressing the device Back button after sign-out must not allow the user to return to authenticated screens.

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
- Blocks and reports referencing the user must be cleanly unlinked without leaving broken references.
- After server-side deletion, the app executes the local sign-out and cleanup sequence ([AUTH-06]).

### Acceptance Criteria
- [ ] Given an authenticated user, when they confirm account deletion in Settings, then all inventory items, friendships, focus sessions, and profile records are deleted.
- [ ] Given successful account deletion, then local session credentials, local task caches, and push tokens are wiped and the user is placed on the Login screen.
- [ ] Given a deleted user account, when attempting to sign in again with the old credentials, then the account is treated as non-existent.
- [ ] Given existing friends of the deleted user, when they view their Friends list, then the deleted user no longer appears.

### Open Questions
- When a user is deleted, should their submitted violation reports be retained anonymously for moderation audit? (The system currently unlinks the reporter identity on delete, preserving the report reason).

---

## [AUTH-09] Persistent Encrypted Token Storage & Cold-Start Auto-Login
**Priority:** High  
**Status:** `Implemented`

### Description
As a returning user, I want my authentication session token to be securely persisted on my device so that closing or force-stopping the app does not log me out on the next launch.

### Business Rules & Constraints
- Store the JWT/session token in Android encrypted local storage (Jetpack Security `EncryptedSharedPreferences` or encrypted DataStore) inside `data/local/` (never exposed outside `data/`).
- On app cold start, `RemoteAuthRepository` reads the stored token, validates it against `GET /api/auth/me`, and transitions directly to `AuthStatus.Authenticated` if valid.
- If `GET /api/auth/me` returns `401 Unauthorized`, clear the stored token and transition cleanly to `AuthStatus.NotAuthenticated` (Login screen).
- If the device is offline on cold start with a stored token, retain the token and display the offline recovery dialog rather than logging the user out ([AUTH-04]).
- Signing out or deleting the account immediately wipes the persisted token ([AUTH-06], [AUTH-07]).

### Acceptance Criteria
- [ ] Given a logged-in user force-closes and relaunches the app while online, then they land directly on Home without seeing the Login screen.
- [ ] Given an expired or revoked stored token on cold start, then the stored token is cleared and the user is routed to Login.
- [ ] Given a user signs out or deletes their account, then the persisted token is completely removed from device storage.
- [ ] Clean architecture check (`ArchitectureBoundaryTest`) continues to pass with token storage encapsulated inside `data/`.

---
---

# Epic: Live Focus Room & Body Doubling

**Goal:** Provide an engaging, real-time virtual body-doubling room where users can focus on their personal tasks alongside other active participants, broadcast live progress, and stay motivated.  
**Scope:**
- Focus Session Initialization
- Task CRUD (Add, Toggle Status, Delete, Active Task Selection)
- Local Offline Task Persistence & Recovery
- Live Room Presence and Real-Time Task Broadcasting
- Live Participant Presence & Activity Tracking
- Friend Notification Trigger on Entry

---

## [ROOM-01] Enter Focus Room & Start Session
**Priority:** High

### Description
As a user, I want to enter the Focus Room from the Home screen so that a new tracked focus session begins and I can start working.

### Business Rules & Constraints
- Entering the room initiates a focus session with the current start time, unprocessed status, and initial zeroed counters (0 tasks completed, 0 minutes, 0 leaves earned).
- If a session is already active (e.g., returning from an app process restoration), the existing session ID must be restored rather than starting a duplicate.
- Entering the room automatically triggers push notifications to all accepted friends (see [SOC-05]).
- A user can rapidly exit and re-enter a focus session without creating duplicate presences or phantom participants.

### Acceptance Criteria
- [ ] Given the user is on the Home screen, when they tap "Start Focus", then a focus session is created with the current timestamp and they are navigated to the Focus Room.
- [ ] Given the room is opened, then the user joins the live focus room and broadcasts their active presence to other participants.
- [ ] Given the user has accepted friends with registered push tokens, when the session starts, then a notification is dispatched to those friends.
- [ ] Given an active ongoing session, when the user returns to the room after momentary app interruption or navigation, then the existing session is resumed without creating a new duplicate session.

### Open Questions
- None.

---

## [ROOM-02] Manage Session Tasks
**Priority:** High

### Description
As a user in the Focus Room, I want to add, select, complete, and delete tasks so that I can track what I am accomplishing during my focus period.

### Business Rules & Constraints
- **Adding Tasks:**
  - Tasks require a non-blank text description and are sanitized and capped at **120 characters** (`TextLimits.MAX_TASK_TEXT_LENGTH` on Android and clamped on backend completion / WebSocket presence).
  - Adding a task when no active task is selected automatically designates the new task as the active task.
  - New tasks default to unfinished status.
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
- **Live Presence & Task Synchronization:**
  - Participant task updates and room presence are synchronized in real time so active participants observe each other's progress promptly.
  - Updates are handled efficiently to maintain room stability and smooth performance.
- The user's own card is excluded from the list of other participants.
- Participants on the user's blocked list must be filtered out immediately and must never appear in the room ([SAFE-02]).
- When a participant leaves or disconnects, their card is removed from the list.
- **Room Capacity & Future Custom Rooms:**
  - The application operates with a single global focus room. Private rooms with custom titles are planned for future releases.
  - A room capacity limit is enforced to maintain high performance and prevent room over-saturation.

### Acceptance Criteria
- [ ] Given another user joins the room, when their presence is detected, then an avatar card showing their name, active task text, and task count appears in the UI.
- [ ] Given a remote participant changes their active task or marks a task complete, then their card updates in real time.
- [ ] Given a remote participant is on the current user's blocked list, then their presence is hidden and not displayed in the room.
- [ ] Given the user leaves the room, then their presence is removed from other participants' screens promptly.
- [ ] Given the global focus room reaches its active participant capacity ceiling, then additional join requests are rejected gracefully with a capacity notice.

### Open Questions
- None.

---

## [ROOM-05] Notify Friends Upon Entering Room
**Priority:** Medium

### Description
As a focusing user, I want my friends to be notified when I enter a focus room so that they know I am studying and can join me.

### Business Rules & Constraints
- Notification is dispatched when the user starts a session upon entering the room.
- Only friends with accepted friendship status are notified.
- Friends without push notifications enabled on their device are gracefully skipped.
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

## [ROOM-06] Robust Background Focus Continuity (Foreground Service, Persistent Notification & Floating Mini-Widget)
**Priority:** High  
**Status:** `Implemented`

### Description
As a user who switches to another app (e.g., notes, PDF reader, Anki, browser) or locks my screen during a focus session, I want my focus session and live Focus Room connection to stay reliably active in the background with a persistent notification and optional floating mini-widget so that Android does not drop my session and I can glance at or control my focus progress from anywhere.

### Business Rules & Constraints
- **Foreground Service & Persistent Notification (Core Reliability):**
  - Entering the Focus Room starts a dedicated Android Foreground Service (`FocusSessionService`, `foregroundServiceType="specialUse"` / `dataSync` compliant with Android 14+ / API 34–37) that binds to the active session lifecycle.
  - Displays a low-noise, ongoing (non-swipeable) notification while a session is active showing:
    - Elapsed session duration (live chronometer)
    - Current active task title (or `"Focusing in Cozy Room"`)
    - Current participant count (`"X focusing now"`)
  - Notification quick actions:
    - **Return to Room** (tap notification body → brings Focus Room to foreground without creating a duplicate session)
    - **Complete Active Task** (marks active task done and syncs progress without leaving the current app)
    - **Finish Session** (cleanly stops service, calls `finishSession`, and opens Summary)
  - Keeps the `/ws/room` WebSocket connection alive with heartbeat pings and automatic exponential-backoff reconnection if the OS briefly throttles network access.
- **Optional Floating Mini-Widget / Picture-in-Picture (PiP) Overlay:**
  - Users can opt in (via a toggle in Settings or a Focus Room top-bar button) to show a compact floating focus pill when backgrounding the app.
  - Preferred implementation: Android **Picture-in-Picture (PiP)** mode (requires zero special overlay permissions and works cleanly across Android 12–17), displaying elapsed time, active task title, and co-worker count, with tap-to-expand back into the room.
  - Optional enhancement: Home-screen Glance widget showing active session status or quick `"Start Focus"` launcher.
- **Live Room Time Sync & Connection Status:**
  - Server pushes verified `active_seconds` on `/ws/room` connect/reconnect (`session_sync`) and heartbeats (`pong`) so the room timer, PiP overlay, and persistent notification chronometer remain synchronized after network drops.
  - Focus Room shows a subtle `Connecting…` / `Reconnecting…` indicator whenever WebSocket status is not `CONNECTED`.
- **Unexpected Closure & Process-Death Recovery:**
  - Persist active session state synchronously (`.commit()`) while a session is active, and clear it upon normal `finishSession()`, sign-out, or account deletion.
  - Keeping `CozyDoublingApp` mounted during PiP ensures expanding PiP back to fullscreen returns directly to the active Focus Room without losing the backstack.
  - If the app is swiped away (`onTaskRemoved` / `onCleared`) or killed mid-session, `InterruptedSessionRecoveryCoordinator` detects the interrupted session on next cold start, finalizes verified progress idempotently on the server (`end_reason = "app_closed"`), and presents the Summary screen clearly explaining what happened.
- **Clean Architecture & Privacy Constraints:**
  - `FocusRoomViewModel` interacts only with domain interfaces (`FocusSessionLifecycleController`, `TextLimits`) in `core/` — never importing Android `Service`, `NotificationManager`, or `data.*` classes directly.
  - Explicit "Start Focus" clears any leftover persisted session ID so stale sessions never hijack a brand-new focus session (`recovery = false` vs `recovery = true`).
  - Stopping or finishing a session, signing out, or deleting the account immediately stops the foreground service and dismisses the persistent notification.

### Acceptance Criteria
- [ ] Given a user enters the Focus Room and switches to another app for 5+ minutes, then the session stays connected in `/ws/room`, other participants still see the user, and no duplicate session is created upon returning.
- [ ] Given an active Focus Room session, then a persistent ongoing notification displays the live elapsed timer, active task, and quick actions (`Return`, `Complete Task`, `Finish`).
- [ ] Given the user taps `"Complete Task"` on the persistent notification while in another app, then the active task is marked completed and peers in the Focus Room see the updated completion count.
- [ ] Given PiP / floating mini-widget mode is enabled, when the user navigates home from Focus Room, then a compact floating pill displays elapsed time and active task and expands back to Focus Room when tapped.
- [ ] Given the user finishes the session or signs out, then the foreground service stops immediately and the persistent notification is dismissed.
- [ ] Given the app process is killed mid-session by the OS, when relaunched, then the app cleanly resumes the active Focus Room session ID.

---

## [ROOM-07] Structured Focus Timer & Pomodoro Interval Modes
**Priority:** High  
**Status:** `Planned`

### Description
As a user in the Focus Room, I want to choose between open-ended focus or structured timer presets (e.g., 25/5 Pomodoro, 50/10 Deep Work, or custom duration) with gentle completion chimes so that I can pace my work and breaks effectively.

### Business Rules & Constraints
- Users can pick **Open Focus** (default count-up) or a **Timed Interval** (25m, 50m, custom 5–120m) before or inside the Focus Room.
- When a countdown finishes, play a gentle optional chime/vibration and prompt to either **Take a Break**, **Extend +10m**, or **Finish Session**.
- Server-side leaf calculation remains strictly based on verified server timestamps ([ECON-01]), unaffected by local countdown presets.

### Acceptance Criteria
- [ ] Given a user selects a 25-minute preset, then the Focus Room displays a live countdown ring/timer alongside the participant grid.
- [ ] Given the countdown reaches 0, then a gentle notification/prompt offers Extend, Break, or Finish without abruptly discarding active tasks.

---

## [ROOM-08] Private / Friend-Only Focus Rooms with Invite Codes
**Priority:** Medium  
**Status:** `Planned`

### Description
As a user studying with specific friends, I want to create or join a private focus room with a short room code and optional topic so that we can focus together in a quiet, invite-only space.

### Business Rules & Constraints
- Users can join the default **Global Cozy Room** or create a **Private Room** with a 6-character invite code and optional room title (e.g., `"Finals Prep"`).
- Backend WebSocket `/ws/room` scopes presence state, updates, and capacity limits by `room_id` (default `"global"`).
- Empty private rooms are cleaned up automatically once the last participant disconnects.

### Acceptance Criteria
- [ ] Given user A creates a private room, then only users who join with that room's invite code appear in its participant grid.
- [ ] Given user B is in the global room and user A is in a private room, then neither sees the other's presence.

---

## [ROOM-09] Task Editing, Reordering & Ambient Cozy Soundscapes
**Priority:** Low  
**Status:** `Planned`

### Description
As a focusing user, I want to edit typos in unfinished tasks, reorder my task list, and optionally toggle subtle ambient sounds (rain, fireplace, cafe) so that my workspace feels calm and frictionless.

### Business Rules & Constraints
- Unfinished tasks support inline text editing and drag-to-reorder; completed tasks remain immutable.
- Ambient soundscapes play locally with independent volume control and pause cleanly on audio focus loss.

### Acceptance Criteria
- [ ] Given an unfinished task, when the user edits its text, then the updated text persists locally and broadcasts to room peers if active.
- [ ] Given ambient sound is enabled, when the user exits the Focus Room, then playback fades out and stops cleanly.

---

## [ROOM-10] Home-Screen Focus Glance Widget
**Priority:** Low  
**Status:** `Planned`

### Description
As a user, I want an optional Android home-screen Glance widget showing my active session elapsed time or a one-tap `"Start Focus"` shortcut so that entering or checking a session feels effortless.

### Business Rules & Constraints
- Displays current active task & elapsed session timer when `FocusSessionLifecycleController.activeSession` is active, or a calm `"Start Focus"` CTA when idle.
- Reads state cleanly via `core/` abstractions and deep-links into the Focus Room without creating duplicate sessions.

### Acceptance Criteria
- [ ] Given an active focus session, the home-screen widget reflects the current active task and links directly back to the Focus Room.
- [ ] Given no active session, tapping `"Start Focus"` on the widget opens Cozy Doubling directly into a fresh session.

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
- Tapping "Finish Work" disconnects from the live room, finalizes the session, and triggers reward calculation.
- **Server-Side Duration & Reward Verification:**
  - Session duration in minutes is derived exclusively on the server using verified start and completion timestamps to prevent client clock manipulation.
  - Focus duration cannot be modified or overridden by the client device.
- A focus session can only be finalized and rewarded once. Subsequent completion attempts for an already completed session must be rejected.

### Acceptance Criteria
- [ ] Given an active focus session, when the user taps "Finish Work", then the session is finalized with verified duration and rewards.
- [ ] Given a session started 25 minutes ago according to server time, when finalized, then the verified duration is 25 minutes regardless of device clock settings.
- [ ] Given a session has already been finalized, when another finish request is received for the same session, then the request is rejected with `"Session not found or already processed."`
- [ ] Given successful session completion, then the app transitions immediately to the Summary screen displaying the session accomplishment.

### Open Questions
- None.

---

## [ECON-02] Leaf & Minutes Calculation Formula
**Priority:** High

### Description
As a user, I want to earn Leaves and accumulate focus minutes based on my focus duration and task completions so that I can buy shop items and track my milestone growth.

### Business Rules & Constraints
- **Anti-farming limits:** at most `MAX_REWARDED_TASKS_PER_SESSION` (default 20) completed tasks earn leaves per session; finishing a session ID that was never started returns `404` (`"Session not found or already processed."`).
- **Reward Formula:**
  $$\text{earned\_leaves} = (\text{duration\_minutes} \times 1) + (\text{tasks\_completed} \times 3)$$
- **Minimum Reward Floor:**
  - If `earned_leaves < 1`, `earned_leaves` is set to `1` (ensures even brief or 0-task test sessions grant at least 1 Leaf).
- **Profile Statistics Update:**
  - The user's leaf balance is incremented by the earned leaves.
  - The user's lifetime focus minutes counter is incremented by the verified session duration.
- Balance and statistic updates are performed exclusively on the server and cannot be modified directly by client devices.

### Acceptance Criteria
- [ ] Given a 30-minute session with 3 completed tasks, when finalized, then the earned leaves equal $(30 \times 1) + (3 \times 3) = 39$ leaves.
- [ ] Given a 0-minute session with 0 completed tasks, when finalized, then the earned leaves equal 1 leaf (minimum floor).
- [ ] Given a user with an initial balance of 50 leaves and 100 focus minutes, when finishing a 20-minute session with 2 tasks ($20 + 6 = 26$ leaves), then their new balance is 76 leaves and total focus minutes is 120.

### Open Questions
- Resolved: Rewardable duration per session is capped at 720 minutes (12 hours) in `[ECON-04]`.

---

## [ECON-03] Post-Session Summary Screen
**Priority:** High

### Description
As a user who just finished a session, I want to see a celebration summary of my minutes, tasks, and leaves earned so that I feel a sense of completion.

### Business Rules & Constraints
- The Summary screen loads the finalized session data.
- Displays:
  - Focused Minutes
  - Tasks Finished
  - Leaves Earned
- Tapping "Continue" navigates back to the Home screen, and the user cannot navigate back into the finished focus room.

### Acceptance Criteria
- [ ] Given a finished session with 45 minutes, 4 tasks, and 57 leaves, when the Summary screen opens, then all three values match the recorded session data.
- [ ] Given the user taps "Continue" on the Summary screen, then they are returned to the Home screen.
- [ ] Given the user is on the Home screen after viewing the Summary, when pressing device Back, then the app exits rather than reopening the finished Focus Room.

### Open Questions
- None.

---

## [ECON-04] Session Duration Cap & Abandoned Session Auto-Finalize
**Priority:** Medium  
**Status:** `Implemented`

### Description
As a product owner protecting the fairness of the Leaf economy while welcoming marathon deep-work studiers, I want focus sessions to track server-verified connected time with fair disconnect grace, automatic abandonment closure, and a 12-hour per-session cap so honest 12-hour studiers earn uninterrupted full rewards while users who leave early or disconnect cannot farm passive Leaves.

### Business Rules & Constraints
- **12-Hour Hard Cap:** Rewardable duration per session is capped at **720 minutes (12 hours)** (`end_reason = "capped_max_duration"`).
- **Brief-Disconnect Grace (`<= 10 minutes`):** Short network drops count seamlessly toward verified active focus time (`0` minutes lost).
- **Offline Gap Exclusion (`10–30 minutes`):** Reconnecting or finishing after a 10–30 minute offline gap excludes the unpaid gap, sets `was_capped = true`, and records `end_reason = "completed_with_offline_gap"` (distinct from `"capped_max_duration"` so Summary displays an accurate offline-gap notice rather than the 12-hour cap message).
- **Abandonment Timeout (`> 30 minutes`):** Disconnecting for `> 30` minutes (or never connecting to `/ws/room`) auto-finalizes the session cleanly at `last_disconnected_at` (`end_reason = "abandoned_disconnect"`), granting `0` leaves if both verified minutes and completed tasks equal `0`.
- **Unexpected App Closure (`app_closed`):** Closing/swiping away the app mid-session triggers idempotent finalization on next launch using server-verified connected seconds (`end_reason = "app_closed"`), granting `0` leaves if `0m` and `0` tasks, and explaining what happened on Summary.
- **Single Active Session:** Starting a new session automatically finalizes any prior unprocessed session for that user.
- The Summary screen displays an accurate header and notice banner driven by `resolveSummaryPresentation` (`app_closed`, `abandoned_disconnect`, `completed_with_offline_gap`, `capped_max_duration`, or `loadError`).

### Acceptance Criteria
- [x] Given an uninterrupted 5h–12h marathon session (even without clicks in background), all verified minutes up to 720m are rewarded.
- [x] Given an 18-hour session, rewarded duration is capped at 720 minutes (`was_capped = true`, `end_reason = "capped_max_duration"`).
- [x] Given a user disconnects after 25m and is gone for 3 hours, the session auto-finalizes at 25m (`end_reason = "abandoned_disconnect"`), and a 0m/0-task immediate abandon grants `0` leaves.

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
- **Relationship Uniqueness:**
  - A friendship between two users is bidirectional and unique; duplicate relationships or duplicate requests between the same two users are prevented.
  - If the users are already friends, the request fails with `"Already friends!"`.
  - If the current user has an outstanding sent request to this player, the request fails with `"Request already sent!"`.
  - A valid request creates a pending friend invitation.

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
  - If an incoming pending request already exists from user B, the system treats user A's action as an acceptance.
  - The friendship status becomes accepted immediately.
  - Both users become friends immediately without requiring user A to manually locate the request in their incoming requests list.

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
  - The incoming requests list displays pending friend requests sent by other players.
  - Requests from blocked users are excluded ([SAFE-02]).
- **Accept Request:**
  - Confirms the friendship as accepted.
  - Both players become friends and can view each other's focus stories.
- **Decline Request:**
  - Rejects and removes the pending request.
  - The sender is not notified of the decline.

### Acceptance Criteria
- [ ] Given an incoming request from user "Leo", when the user taps "Accept", then the request moves to the active Friends list with status `accepted`.
- [ ] Given an incoming request from user "Leo", when the user taps "Decline", then the request is removed from the pending list and deleted.
- [ ] Given an accepted friendship, then both users start receiving room entry push notifications for each other.

### Open Questions
- Resolved: Bidirectional block enforcement returns `HTTP 403` with clear user error messages on send/accept (`BUG-1`), and real-time `/ws/friends` updates + pull-to-refresh + tab badge keep requests synced immediately (`Bug-2`).

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
**Priority:** High  
**Status:** `Implemented`

### Description
As a user, I want to remove an existing friend from my Friends list so that we no longer share focus activity or receive each other's focus notifications, without having to block them.

### Business Rules & Constraints
- Backend endpoint `DELETE /api/friends/{friend_id}` is already implemented and tested; Android client needs:
  - `FriendsRepository.removeFriend(friendId: String)` + `RemoteFriendsRepository` calling `DELETE /api/friends/$friendId` and refreshing friends list.
  - `FriendsViewModel.onRemoveFriend(friendId: String)` emitting a snackbar confirmation (`"Friend removed"`).
  - `FriendCard` `"More options"` dropdown menu item `"Remove Friend"` (distinct from `"Report User"` and `"Block User"`) opening a confirmation `AlertDialog` (`"Remove [Name]?"` with `"Remove"` / `"Cancel"`).
- Upon confirmation:
  - Both users are removed from each other's Friends lists (no block is created).
  - Room entry push notifications are no longer dispatched between the two users ([ROOM-05]).
  - Either user may send a new friend request in the future using the standard player tag flow ([SOC-01]).

### Acceptance Criteria
- [ ] Given user A and user B are accepted friends, when user A opens `"More options"` → `"Remove Friend"` and confirms, then `DELETE /api/friends/{friend_id}` succeeds and user B disappears immediately from user A's Friends list.
- [ ] Given user A taps `"Remove Friend"` and taps `"Cancel"` in the confirmation dialog, then user B remains in user A's Friends list.
- [ ] Given user A unfriends user B, when user B refreshes Friends, then user A no longer appears in user B's Friends list.
- [ ] Given user A unfriends user B, then either user can later send a new friend request to the other without being blocked.
- [ ] Covered by real 2-device E2E test in `TwoDeviceFriendsAndSafetyE2eTest`.

---

## [SOC-06] View & Cancel Outgoing Pending Friend Requests
**Priority:** Medium  
**Status:** `Planned`

### Description
As a user who sent a friend request by mistake or to someone who hasn't responded, I want to view and cancel my outgoing pending requests so that my pending connections stay tidy.

### Business Rules & Constraints
- Backend adds `GET /api/friends/requests/outgoing` and `DELETE /api/friends/requests/{target_id}` (restricted to rows where `requester_id == my_id` and `status == 'pending'`).
- Friends screen displays an optional `"Sent Requests (N)"` section with a `"Cancel"` button per pending outgoing request.

### Acceptance Criteria
- [ ] Given user A sent a pending request to user B, then user A sees user B under `"Sent Requests (1)"` with a `"Cancel"` action.
- [ ] Given user A cancels the outgoing request, then the pending row is deleted and user B no longer sees an incoming request from user A.

---

## [SOC-07] Friend Notification Mute Toggle & Targeted Room Invites
**Priority:** Medium  
**Status:** `Planned`

### Description
As a user with many friends, I want to mute automatic friend room-entry notifications in Settings and optionally nudge/invite a specific friend to join my active Focus Room.

### Business Rules & Constraints
- Profile adds a `mute_friend_notifications: Boolean` preference (default `false`), editable from Settings.
- Backend skips push dispatch in `/api/sessions/start` for friends with `mute_friend_notifications == true`.
- Optional `"Invite to Focus"` action on `FriendCard` sends a rate-limited targeted push notification to that friend.

### Acceptance Criteria
- [ ] Given user B enables `"Mute Friend Notifications"` in Settings, when user A starts a focus session, then user B receives no push notification.
- [ ] Given user A taps `"Invite to Focus"` on friend B, then friend B receives a targeted invite notification (unless muted or blocked).

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
- **Atomic Purchase:**
  - If all checks pass, the purchase is processed atomically: the leaf price is deducted from the user's balance and the theme is unlocked in their inventory simultaneously.
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
  - The supporter entitlement becomes active for the account.
  - The user's profile is updated with supporter status.
  - All premium themes become instantly available for equipping in Inventory.
- If a subscription expires or is revoked:
  - Supporter status is deactivated.
  - **Auto-Revert on Expiration:** If the user currently has a premium theme equipped, the system automatically unequips the premium theme and reverts to the system default theme upon the next profile synchronization.

### Acceptance Criteria
- [ ] Given a free user, when they complete a Supporter Pass purchase, then their account gains active supporter status.
- [ ] Given an active supporter, then all premium themes in the Shop and Inventory indicate unlocked status.
- [ ] Given a purchase attempt that is cancelled by the user in the Google Play dialog, then no charges occur and supporter status remains inactive.
- [ ] Given a user with a premium theme equipped whose subscription expires, when profile status syncs, then supporter status is deactivated and the equipped theme automatically reverts to the System Default.

### Open Questions
- None.

---

## [SHOP-04] Restore Previous In-App Purchases
**Priority:** Medium

### Description
As a returning user or user on a new device, I want to restore my purchases from Settings so that my active Supporter status is recognized without paying again.

### Business Rules & Constraints
- Tapping "Restore Purchases" in Settings checks Google Play for active subscription entitlements under the user's Google Play account.
- If an active supporter pass is found:
  - The active entitlement is verified and linked to the user's profile.
  - The user's supporter status is activated.
  - The UI displays `"Purchases restored successfully!"`.
- If no active entitlement is found, the UI displays `"No active purchases found to restore."`.

### Acceptance Criteria
- [ ] Given a user with an active subscription on Google Play, when they tap "Restore Purchases", then supporter status is activated and a success confirmation is shown.
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
- Flicker-Free Theme Presentation

---

## [THEME-01] View Owned and Supporter-Unlocked Themes
**Priority:** High

### Description
As a user in the Oasis Inventory, I want to view all the themes I own or have unlocked so that I can choose which look to apply to my app.

### Business Rules & Constraints
- The Inventory list includes:
  1. **System Default Theme:** Always owned and available.
  2. **Purchased Themes:** Themes acquired with leaves by this user.
  3. **Supporter Themes:** Premium themes unlocked via an active Supporter Pass.
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
  - The item must be a valid theme.
  - The user must either own the theme or have an active Supporter Pass for premium themes.
  - If validation fails, the action is rejected with `"You do not own this item or have an active Supporter Pass."`
- **Application:**
  - Upon equipping, the theme is saved as the user's active theme.
  - The application dynamically updates its complete color palette to match the equipped theme.
  - All screens across the application update immediately without requiring an app restart.
  - Destructive action colors (such as "Delete Account" red) intentionally remain fixed and are not recolored by theme palettes.
- **Lapsed Supporter Auto-Reversion:**
  - If a user has a premium theme equipped and their Supporter Pass lapses or is cancelled, the system automatically unequips the premium theme and resets to System Default ([SHOP-03]).

### Acceptance Criteria
- [ ] Given an owned theme, when the user taps "Equip", then the theme becomes active across the app.
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
- Selecting the "System Default" theme unequips any custom theme.
- The app immediately reverts its visual color palette to the default brand theme (cream background and sage primary).

### Acceptance Criteria
- [ ] Given a custom theme currently equipped, when the user selects "System Default", then the custom theme is unequipped.
- [ ] Given the system default theme is active, then the app renders using the default brand colors (cream background and sage primary).

### Open Questions
- None.

---

## [THEME-04] Flicker-Free Theme Presentation
**Priority:** Medium

### Description
As a user navigating between screens or resuming the app, I want my selected theme to remain visually stable so that screens do not flash between default and custom colors.

### Business Rules & Constraints
- The active visual theme is preserved seamlessly across app navigation, tab switching, and backgrounding.
- Network revalidations or profile refreshes must not cause the UI to flash or temporarily fall back to a default theme.
- When a user explicitly equips a new theme, the visual style transitions smoothly to the newly selected theme.

### Acceptance Criteria
- [ ] Given a user with a custom theme equipped, when navigating between tabs or resuming the app from background, then the interface remains visually stable without flickering to default colors.
- [ ] Given an initial cold launch, the app displays a solid brand splash until the active session and theme styling are ready for presentation.

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
- Blocking establishes an active block between the two users.
- If the blocked user was an accepted friend, the active block overrides and hides the relationship.
- Blocking is idempotent: attempting to block an already-blocked user maintains the blocked status without errors.

### Acceptance Criteria
- [ ] Given user A taps "Block" on participant user B in the Focus Room, then user B is recorded as blocked by user A.
- [ ] Given user A blocks user B, then user B's participant card immediately disappears from user A's Focus Room.
- [ ] Given user A blocks user B who is an existing friend, then user B no longer appears in user A's active Friends list.

### Open Questions
- Resolved: Manage Blocked Users & Unblock UI implemented in Settings (`[SAFE-04]`).

---

## [SAFE-02] Real-Time Filtering of Blocked Users
**Priority:** High

### Description
As a user, I want blocked user filtering to apply in real time so that blocked players are instantly hidden without restarting the app.

### Business Rules & Constraints
- The app maintains an active blocked set including users blocked by the current user and users who have blocked the current user.
- When a block occurs, a real-time event updates the active blocked user set immediately.
- **Filter Enforcements:**
  1. *Focus Room:* Participants in the blocked set are filtered out of the participant list.
  2. *Friends List:* Friends or incoming requests matching the blocked set are filtered out.
  3. *Push Notifications:* Notifications from blocked users are suppressed.

### Acceptance Criteria
- [ ] Given user A and user B are in the same Focus Room, when user A blocks user B, then user B's card is immediately removed from user A's room view.
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
  - The identified target player.
  - A non-blank description of the violation provided by the reporter.
- The report is recorded with reporter attribution and submission timestamp for administrative moderation review.
- After submitting a report, the UI confirms with `"Report submitted. Thank you."`.

### Acceptance Criteria
- [ ] Given a user taps "Report" on a participant and provides reason `"Offensive name"`, then the violation report is submitted with the target, reason, and reporter details.
- [ ] Given a successful report submission, then the UI displays `"Report submitted. Thank you."`.
- [ ] Given an empty report reason, then submission is prevented until text is entered.

### Open Questions
- None.

---

## [SAFE-04] Manage & Unblock Blocked Users in Settings
**Priority:** High  
**Status:** `Implemented`

### Description
As a user who previously blocked someone (accidentally or after resolving a misunderstanding), I want to view my list of blocked users in Settings and unblock them so that I can manage my safety list without needing support intervention.

### Business Rules & Constraints
- Backend endpoint `POST /api/safety/unblock` and real-time `/ws/safety` `block_removed` broadcast are already implemented; backend adds `GET /api/safety/blocked-profiles` (returning `id`, `display_name`, `player_tag` for users blocked by the current user, never revealing users who blocked them).
- Settings adds a `"Blocked Users"` row opening a sheet/screen listing blocked accounts with an `"Unblock"` action and confirmation dialog.
- Unblocking immediately updates the active blocked set in `SafetyRepository` via `/ws/safety` (`block_removed`) so the user can appear in Focus Rooms and receive friend requests again.

### Acceptance Criteria
- [ ] Given user A blocked user B, when user A opens Settings → `"Blocked Users"`, then user B appears in the list with an `"Unblock"` button.
- [ ] Given user A unblocks user B, then user B disappears from `"Blocked Users"` and both users can see each other in the Focus Room again without restarting the app.
- [ ] Given user B blocked user A, then user B does NOT appear in user A's `"Blocked Users"` list.

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
  - **Username:** Current display name.
  - **Account Longevity Bio:** Formatted as `"Cozy Doubler since [YYYY-MM]"` derived from the account's creation date.
  - **Total Leaves:** Current accumulated leaf balance.
  - **Total Focus Minutes:** Lifetime verified focus minutes counter.
- Stats update automatically whenever profile sync events occur (such as completing a focus session or buying a theme).

### Acceptance Criteria
- [ ] Given a user created their account in March 2026, then the bio displays `"Cozy Doubler since 2026-03"`.
- [ ] Given a user with 250 leaves and 1,420 total focus minutes, then both figures are accurately rendered on the dashboard.
- [ ] Given the user completes a 30-minute focus session, when they return to Journey, then total focus minutes reflects the 30-minute increase.

### Open Questions
- Restore `"Cozy Doubler since YYYY-MM"` bio line in `JourneyPage.kt` (currently commented out in UI).

---

## [JOURN-02] Focus Session History, Daily Streaks & Milestones
**Priority:** Medium  
**Status:** `Planned`

### Description
As a regular user, I want to see my recent completed focus sessions, my daily focus streak, and gentle milestone badges in the Journey tab so that I stay motivated over weeks and months.

### Business Rules & Constraints
- Backend adds `GET /api/sessions/history?limit=20` returning the authenticated user's processed sessions (date, duration, tasks completed, leaves earned, last task text) and computed current/longest daily streak.
- Journey tab renders a calm streak indicator, weekly focus summary, and scrollable recent session list without harsh "streak-broken" shame messaging (staying true to Cozy Doubling's calm brand).

### Acceptance Criteria
- [ ] Given a user completes 3 focus sessions across 3 consecutive days, then Journey displays a 3-day focus streak and lists all 3 recent sessions.
- [ ] Given a new user with 0 completed sessions, then Journey displays a warm empty state encouraging their first session.

---
---

# Epic: UX Polish, Accessibility & Localization

**Goal:** Ensure layouts stay resilient across device accessibility settings and screen sizes, prevent oversized user text from breaking cards, and deliver consistent multilingual translations.  
**Scope:**
- Font Scale Bounds (`TextLimits`)
- Input Character Caps Across Client & Server
- Localization String Parity

---

## [UI-01] Typography Font-Scale Cap & Input Length Guardrails
**Priority:** High  
**Status:** `Implemented`

### Description
As a user with large system accessibility font sizes or entering long text, I want Cozy Doubling to cap extreme font scaling and enforce sensible input length bounds so cards, pills, and buttons never overlap or overflow awkwardly.

### Business Rules & Constraints
- **Font Scale Cap (`CozyDoublingTheme`):** `LocalDensity.fontScale` is bounded via `TextLimits.clampFontScale` (`0.85f..1.25f`).
- **Input & Server Bounds:**
  - Display Name: max **30** characters (`ProfileUpdateRequest`, `UserRegisterRequest`, `SettingsPage`, `SettingsViewModel`, `/ws/room`).
  - Focus Task Text: max **120** characters (`FinishSessionRequest`, `FocusRoomPage`, `FocusRoomViewModel`, `/ws/room`).
  - Connect / Friend Code: max **9** chars in UI input (`16` max on API to allow optional formatting/whitespace).
  - Report Reason: **1–200** characters (`ReportUserRequest`).
  - Auth Email / Password: max **254** / **128** characters.

### Acceptance Criteria
- [x] Given OS font scale is set to `2.0x`, `CozyDoublingTheme` clamps effective `fontScale` to `1.25f`.
- [x] Given a user types or sends a 50-char display name or 200-char task/report, UI and backend enforce limits cleanly without layout breakage.

---

## [UI-02] Multi-Language Localization Parity
**Priority:** Medium  
**Status:** `Planned`

### Description
As an international user whose device uses Turkish, German, Spanish, French, or Italian, I want all newer UI notices, status badges, and settings strings to appear in my selected language rather than falling back to English.

### Business Rules & Constraints
- Audit `values-tr`, `values-de`, `values-es`, `values-fr`, and `values-it` against base `values/strings.xml` so every newly added key (PiP, Blocked Users, Session Summary closure/offline-gap banners, Focus Room connection status pills) has verified translations.

### Acceptance Criteria
- [ ] Zero untranslated non-brand string keys missing from any supported locale `strings.xml`.
