# Implementation Plan - Home Screen Refresh

We will update the Home screen to match the "Cozy" design image, focusing on the top bar, the central focus indicator, and the bottom navigation styling.

## Proposed Changes

### Core Components

#### [MODIFY] [CozyTopBar.kt](file:///home/emin/repos/android-repos/CozyDoubling/app/src/main/java/com/grepho/cozydoubling/core/components/CozyTopBar.kt)
- Update the layout to include the leaf logo next to the app name.
- Style the currency display as a rounded capsule with a light peach/cream background (`SecondaryContainerPeach`).
- Use the Shop icon (outline) as shown in the design.
- Ensure colors use `PrimarySage`.

### Feature: Home

#### [MODIFY] [HomePage.kt](file:///home/emin/repos/android-repos/CozyDoubling/app/src/main/java/com/grepho/cozydoubling/features/home/HomePage.kt)
- **Central Indicator:** Implement a large circular ring with a soft outer shadow/glow.
- **Focus Button:** Replace the large circular button with a capsule-shaped "Start Focus" button containing a play icon.
- **Description Text:** Add the "Ready for a quiet session?..." text below the indicator.
- **Cleanup:** Remove the Supercell-style player tag if not needed for the Home design.

### Core UI / Navigation

#### [MODIFY] [MainActivity.kt](file:///home/emin/repos/android-repos/CozyDoubling/app/src/main/java/com/grepho/cozydoubling/core/MainActivity.kt)
- Style the `NavigationBar` to use the design's selected state: a sage-colored pill background for the active tab.
- Update icon colors for selected/unselected states.

## Verification Plan

### Manual Verification
- Deploy the app and compare the Home screen with the design image.
- Verify the Top Bar looks identical (logo, capsule currency).
- Verify the "Start Focus" interaction.
- Check the Bottom Navigation's visual feedback when switching tabs.
