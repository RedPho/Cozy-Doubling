# Implementation Plan - Login Screen Refresh

We will update the Login screen to match the provided design, focusing on the soft branding and the stylized "Sign in with Google" button.

## Proposed Changes

### Auth Feature

#### [MODIFY] [LoginPage.kt](file:///home/emin/repos/android-repos/CozyDoubling/app/src/main/java/com/grepho/cozydoubling/features/auth/LoginPage.kt)
- **Background:** Implement a soft background with subtle concentric circles (using `Canvas` or multiple `Box` layers).
- **Branding:**
    - Add the leaf logo (`Icons.Default.Eco`) inside a small circular surface with a subtle shadow.
    - Display the app name "Cozy Doubling" in `PrimarySage` using `titleLarge`.
- **Text Content:**
    - Add "Welcome to Cozy Doubling" as the main headline.
    - Add the sub-text: "A quiet space to focus together, unhurried and calm."
- **Google Sign-In Button:**
    - Design a custom `Surface` that looks like a capsule.
    - White background, light outline.
    - Include the Google "G" logo and the text "Sign in with Google".
- **Footer:** Add the privacy policy and quiet hours notice at the bottom.

## Verification Plan

### Manual Verification
- Deploy the app and view the Login screen (if already logged in, you might need to sign out or clear data).
- Compare the visuals with the design image.
- Verify the "Sign in with Google" button remains functional and looks like the design.
- Check that colors are correctly pulling from `MaterialTheme.colorScheme`.
