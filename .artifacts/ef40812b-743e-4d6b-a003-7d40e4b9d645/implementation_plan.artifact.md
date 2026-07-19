# Implementation Plan - Summary Screen Design Refresh

We will update the Summary screen to match the provided design, focusing on the high-contrast "Leaves Earned" card, the structured "Session Summary" list, and the stylized "Continue" button.

## Proposed Changes

### Summary Feature

#### [MODIFY] [SummaryPage.kt](file:///home/emin/repos/android-repos/CozyDoubling/app/src/main/java/com/grepho/cozydoubling/features/summary/SummaryPage.kt)
- **Header:** Update the title to "Great job showing up today!" in `PrimarySage` and add the subtitle "Your cozy garden is growing.".
- **Reward Card:**
    - Create a large white surface with soft shadows and rounded corners (32dp).
    - Implement a central circular graphic with a leaf icon (`Icons.Default.Eco`) and orbiting small circles.
    - Display the animated leaf count in a large, bold font above the "LEAVES EARNED" label.
- **Session Summary Container:**
    - Add a `Surface` with `SurfaceVariantGrey` background and large rounded corners.
    - Title it "Session Summary" in `PrimarySage`.
    - Create white cards for "Time Focused" and "Tasks Finished".
    - Each row will have a circular icon container, a label, and a bold value.
- **Continue Button:**
    - Style it as a `PrimarySage` capsule with the text "Continue" and `Icons.AutoMirrored.Filled.ArrowForward`.

## Verification Plan

### Manual Verification
- Deploy the app and navigate to the Summary screen (after finishing a focus session).
- Compare the layout and colors with the design image.
- Verify the leaf counting animation still works within the new circular graphic.
- Check the button's appearance and clickability.
