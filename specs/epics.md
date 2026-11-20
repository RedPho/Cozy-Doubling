# Cozy Doubling — Epic Catalog & Story Map

An executive overview of the product domains, goals, scopes, and associated story mappings across Cozy Doubling.

---

## Epic 1: Authentication & Account Lifecycle
**Goal:** Enable users to seamlessly register, authenticate across devices, maintain persistent sessions, customize profile identity, and control their account lifecycle up to complete deletion.  
**Scope:**
- Google One-Tap / Native sign-in and email/password authentication
- Automatic profile initialization with unique 8-character player tag generation
- Session persistence, debounced offline recovery, and token refresh
- Display name customization
- Clean sign-out with local task wiping and push token unlinking
- Permanent 5-step cascade account deletion
- *(Planned)* Guest access mode and self-serve email registration
**Stories:** `[AUTH-01]` to `[AUTH-08]`

---

## Epic 2: Live Focus Room & Body Doubling
**Goal:** Provide an engaging, real-time virtual body-doubling room where users can focus on their personal tasks alongside other active participants, broadcast live progress, and stay motivated.  
**Scope:**
- Starting and resuming focus sessions
- Session task CRUD (add, select active, toggle status, delete unfinished)
- Offline local task persistence across app restarts
- Real-time participant presence and instant task status broadcasting
- Single global room architecture with a 1,000 active participant safety ceiling
- Dispatching push notifications to accepted friends upon entering
**Stories:** `[ROOM-01]` to `[ROOM-05]`

---

## Epic 3: Session Completion & Leaf Economy
**Goal:** Reward users for productive focus time by accurately computing and granting Leaf currency, updating verified focus statistics, and displaying an accomplishment summary.  
**Scope:**
- Ending a focus session with single-processing (`is_processed`) anti-cheat protection
- Server-derived duration computation based strictly on timestamps
- Leaf reward formula: $(duration \times 1) + (tasks \times 3)$, minimum 1 leaf
- Atomic increments to user leaf balance and lifetime focus minutes
- Post-session celebration summary display
**Stories:** `[ECON-01]` to `[ECON-03]`

---

## Epic 4: Friends & Social Activity
**Goal:** Help users stay connected and motivated with study buddies by exchanging player tags, tracking friend activity, and observing friends' focus accomplishments.  
**Scope:**
- Searching players by 8-character player tag
- Sending, accepting, declining, and auto-accepting mutual friend requests
- Removing / unfriending existing friends
- Viewing friends list enriched with recent session duration, task description, and timestamp
- Receiving friend focus room entry push notifications
- *(Planned)* Setting to mute friend alerts and targeted friend nudges/invites
**Stories:** `[SOC-01]` to `[SOC-05]`

---

## Epic 5: Shop & Supporter Subscriptions
**Goal:** Offer a monetized economy where users can purchase aesthetic themes using earned Leaves or subscribe to a Supporter Pass to unlock premium perks.  
**Scope:**
- Browsing themes with full 18-color Material 3 palette mockups
- Purchasing non-premium themes using earned Leaves (balance, ownership, and non-premium checks)
- Subscribing to Supporter Passes (Monthly, Yearly, Lifetime) via Google Play Billing
- Restoring previous in-app purchases
- Deep-linking to Google Play Store subscription management
- Automatic theme reversion to System Default when a Supporter Pass expires
**Stories:** `[SHOP-01]` to `[SHOP-05]`

---

## Epic 6: Inventory & Theme Customization
**Goal:** Allow users to manage their acquired themes, preview cohesive color schemes, and customize the visual aesthetic of the entire application.  
**Scope:**
- Viewing owned themes (System Default + purchased + active supporter unlocked)
- Dynamically equipping theme palettes to recolor all app screens instantly
- Reverting to the system default warm cream and sage palette
- Sticky in-memory theme caching to eliminate screen flickers during app lifecycle events
**Stories:** `[THEME-01]` to `[THEME-04]`

---

## Epic 7: User Safety & Moderation
**Goal:** Protect users from harassment and inappropriate conduct by providing instant blocking and formal violation reporting tools.  
**Scope:**
- Blocking abusive players from focus rooms and friends lists
- Real-time block synchronization to hide blocked players immediately across the app
- Suppressing push notifications and friend requests from blocked users
- Submitting user conduct violation reports with reporter attribution
**Stories:** `[SAFE-01]` to `[SAFE-03]`

---

## Epic 8: Journey & Personal Progress
**Goal:** Give users visibility into their long-term focus habits, accumulated wealth, and milestones through an inspiring personal dashboard.  
**Scope:**
- Account longevity bio (`"Cozy Doubler since YYYY-MM"`)
- Total accumulated Leaf balance display
- Verified lifetime focus minutes counter display
**Stories:** `[JOURN-01]`
