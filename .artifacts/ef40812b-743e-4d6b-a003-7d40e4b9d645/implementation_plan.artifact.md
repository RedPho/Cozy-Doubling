# Implementation Plan - Oasis Hub (Journey & Friends) Refresh

We will update the Oasis Hub, Journey, and Friends pages to match the provided designs, maintaining existing data logic while significantly upgrading the UI styling.

## Proposed Changes

### Oasis Hub Container

#### [MODIFY] [OasisPage.kt](file:///home/emin/repos/android-repos/CozyDoubling/app/src/main/java/com/grepho/cozydoubling/features/oasis/OasisPage.kt)
- Add the "Oasis Hub" title below the top bar (visible for all sub-tabs).
- Style the `SecondaryTabRow` to match the design (centered indicator, sage green accents).
- Ensure the background is `BackgroundCream`.

### Journey Page

#### [MODIFY] [JourneyPage.kt](file:///home/emin/repos/android-repos/CozyDoubling/app/src/main/java/com/grepho/cozydoubling/features/journey/JourneyPage.kt)
- **Profile Section:**
    - Large circular avatar with a subtle ring/glow.
    - Title: "[Name]'s Oasis" using `titleLarge`.
    - Subtitle: "Cozy Doubler since [Date]" in `onSurfaceVariant`.
- **Stats Cards:**
    - Replace the horizontal `GentleStatCard` with two large vertical `Surface` cards.
    - Card 1: "Total Leaves Collected" with a circular icon container (sage/gold) and leaf icon.
    - Card 2: "Time in Deep Focus" with a circular icon container (sage green) and clock icon.
    - Use `SurfaceWhite` with rounded corners and soft shadows.

### Friends Page

#### [MODIFY] [FriendsPage.kt](file:///home/emin/repos/android-repos/CozyDoubling/app/src/main/java/com/grepho/cozydoubling/features/friends/FriendsPage.kt)
- **Connect Code:**
    - Style as a light cream capsule/box with "YOUR CONNECT CODE" label.
    - The code itself in `PrimarySage` with a copy icon.
- **Pending Requests:**
    - Header: "Pending Requests (X)" in `onSurfaceVariant`.
    - Card: White surface, profile pic, "Accept" button (sage green capsule), and "X" decline button.
- **Your Garden:**
    - Header: "Your Garden (X)" with a group icon.
    - Friend Cards:
        - Large white surface with rounded corners.
        - Circular avatar placeholder.
        - Name and tag.
        - Use existing "Last Session" data but style it as per the design.
        - **Note:** No new features (Nudge, Send Leaf, Online Status) will be added to the logic.

## Verification Plan

### Manual Verification
- Deploy the app and navigate to the Oasis Hub.
- Switch between Journey and Friends tabs.
- Verify the Journey page matches Image 4 (Profile, Stats Cards).
- Verify the Friends page matches Image 2 (Connect Code, Pending Requests, Friend Cards).
- Ensure all buttons (Accept, Decline, Copy Code) remain functional.
