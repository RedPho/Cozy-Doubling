# Cozy Doubling — Epic Catalog & Story Map

An executive overview of product domains, goals, current implementation status, and story mappings across Cozy Doubling. Full story specifications live in [01_product_backlog.md](01_product_backlog.md); E2E test automation tickets live in [02_e2e_test_tickets.md](02_e2e_test_tickets.md).

---

## Epic 1: Authentication & Account Lifecycle
**Goal:** Enable users to seamlessly register, authenticate across devices, maintain persistent sessions, customize profile identity, and control their account lifecycle up to complete deletion.  
**Stories:**
- `[AUTH-01]` Sign In with Google *(Implemented)*
- `[AUTH-02]` Sign In with Email & Password *(Implemented)*
- `[AUTH-03]` Automatic Profile & Player Tag Initialization *(Implemented)*
- `[AUTH-04]` Session Persistence & Automatic Re-authentication *(Implemented via `[AUTH-09]`)*
- `[AUTH-05]` Update Display Name *(Implemented)*
- `[AUTH-06]` Sign Out & Device Data Sanitization *(Implemented)*
- `[AUTH-07]` Permanent Account Deletion *(Implemented)*
- `[AUTH-08]` Self-Serve Email Registration *(Implemented)*
- `[AUTH-09]` Persistent Encrypted Token Storage & Seamless Cold-Start Auto-Login *(Implemented)*
- `[AUTH-10]` Password Reset *(Planned — High)*
- `[AUTH-11]` Guest Access Mode *(Planned — Low)*
- `[AUTH-12]` Migrate 1.x (Supabase) Accounts to the New Backend *(Planned — High, release blocker)*

---

## Epic 2: Live Focus Room & Body Doubling
**Goal:** Provide a calm, reliable real-time virtual body-doubling room where users focus on personal tasks alongside active participants — even when switching apps or locking the screen.  
**Stories:**
- `[ROOM-01]` Enter Focus Room & Start Session *(Implemented)*
- `[ROOM-02]` Manage Session Tasks *(Implemented)*
- `[ROOM-03]` Local Task Persistence & Cache Recovery *(Implemented)*
- `[ROOM-04]` Real-Time Participant Presence & Status Broadcasting *(Implemented)*
- `[ROOM-05]` Notify Friends Upon Entering Room *(Implemented)*
- `[ROOM-06]` Robust Background Focus Continuity (Foreground Service, Persistent Notification, PiP Mini-Widget & Interrupted Recovery) *(Implemented)*
- `[ROOM-07]` Structured Focus Timer & Pomodoro Interval Modes *(Planned — High)*
- `[ROOM-08]` Private / Friend-Only Focus Rooms with Invite Codes *(Planned — Medium)*
- `[ROOM-09]` Task Editing, Reordering & Ambient Cozy Soundscapes *(Planned — Low)*
- `[ROOM-10]` Home-Screen Focus Glance Widget *(Planned — Low)*

---

## Epic 3: Session Completion & Leaf Economy
**Goal:** Reward users for productive focus time by accurately computing and granting Leaf currency, preventing abuse, updating verified focus statistics, and displaying an accomplishment summary.  
**Stories:**
- `[ECON-01]` End Focus Session & Server-Side Verification *(Implemented)*
- `[ECON-02]` Leaf & Minutes Calculation Formula *(Implemented)*
- `[ECON-03]` Post-Session Summary Screen *(Implemented)*
- `[ECON-04]` Session Duration Cap & Abandoned Session Auto-Finalize *(Implemented)*

---

## Epic 4: Friends & Social Activity
**Goal:** Help users stay connected and motivated with study buddies by exchanging player tags, managing friendships cleanly, and observing friends' focus accomplishments.  
**Stories:**
- `[SOC-01]` Search Players by Tag & Send Friend Request *(Implemented)*
- `[SOC-02]` Automatic Mutual Request Acceptance *(Implemented)*
- `[SOC-03]` Accept or Decline Pending Friend Requests *(Implemented)*
- `[SOC-04]` View Friends List with Recent Session Stories *(Implemented)*
- `[SOC-05]` Remove / Unfriend an Existing Friend *(Implemented)*
- `[SOC-06]` View & Cancel Outgoing Pending Friend Requests *(Planned — Medium)*
- `[SOC-07]` Friend Notification Mute Toggle & Targeted Room Invites *(Planned — Medium)*

---

## Epic 5: Shop & Supporter Subscriptions
**Goal:** Offer a calm, ad-free economy where users unlock aesthetic themes using earned Leaves or subscribe to a Supporter Pass to unlock premium perks.  
**Stories:**
- `[SHOP-01]` Browse Shop Catalog *(Implemented)*
- `[SHOP-02]` Purchase Themes with Earned Leaves *(Implemented)*
- `[SHOP-03]` Purchase Supporter Pass Subscription & Expiration Auto-Revert *(Implemented)*
- `[SHOP-04]` Restore Previous In-App Purchases *(Implemented)*
- `[SHOP-05]` Manage Subscription Deep-Linking *(Implemented)*

---

## Epic 6: Inventory & Theme Customization
**Goal:** Allow users to manage acquired themes, preview cohesive Material 3 palettes, and customize the visual aesthetic of the entire application without flicker.  
**Stories:**
- `[THEME-01]` View Owned and Supporter-Unlocked Themes *(Implemented)*
- `[THEME-02]` Equip Theme Palette & App-Wide Dynamic Theming *(Implemented)*
- `[THEME-03]` Unequip Theme & Revert to System Default *(Implemented)*
- `[THEME-04]` Flicker-Free Theme Presentation *(Implemented)*

---

## Epic 7: User Safety & Moderation
**Goal:** Protect users from harassment and inappropriate conduct with instant blocking, reversible block management, and formal violation reporting tools.  
**Stories:**
- `[SAFE-01]` Block User from Focus Room or Friends List *(Implemented)*
- `[SAFE-02]` Real-Time Filtering of Blocked Users *(Implemented)*
- `[SAFE-03]` Report Inappropriate User Conduct *(Implemented)*
- `[SAFE-04]` Manage & Unblock Blocked Users in Settings *(Implemented)*

---

## Epic 8: Journey & Personal Progress
**Goal:** Give users visibility into long-term focus habits, streaks, accumulated leaves, and milestones through an inspiring personal dashboard.  
**Stories:**
- `[JOURN-01]` View Personal Journey Dashboard *(Implemented — "Member Since" bio pending UI restore)*
- `[JOURN-02]` Focus Session History, Daily Streaks & Milestones *(Planned — Medium)*

---

## Epic 9: UX Polish, Accessibility & Localization
**Goal:** Keep layouts crisp and legible across accessibility font settings, enforce clean input limits, and support complete multilingual translations.  
**Stories:**
- `[UI-01]` Typography Font-Scale Cap & Input Length Guardrails *(Implemented)*
- `[UI-02]` Multi-Language Localization Parity (`tr`, `de`, `es`, `fr`, `it`) *(Planned — Medium)*
