package com.grepho.cozydoubling.e2e.single

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grepho.cozydoubling.core.MainActivity
import com.grepho.cozydoubling.e2e.E2eTestHarness
import com.grepho.cozydoubling.e2e.addTaskInFocusRoom
import com.grepho.cozydoubling.e2e.openOasisSubTab
import com.grepho.cozydoubling.e2e.openSettingsFromTopBar
import com.grepho.cozydoubling.e2e.LOGIN_SCREEN_MARKER
import com.grepho.cozydoubling.e2e.registerWithEmailUi
import com.grepho.cozydoubling.e2e.signInWithEmailUi
import com.grepho.cozydoubling.e2e.waitUntilNodeDoesNotExist
import com.grepho.cozydoubling.e2e.waitUntilNodeExists
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Single-device E2E covering E2E-02 (Authentication & Account Lifecycle),
 * E2E-09 (Settings Display Name), and E2E-11 (Login/Settings UI edge cases).
 */
@RunWith(AndroidJUnit4::class)
class AuthAndSettingsE2eTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        scenario = E2eTestHarness.launchMainActivity()
    }

    @After
    fun tearDown() {
        scenario?.close()
        E2eTestHarness.resetAppState()
    }

    @Test
    fun emailAuthHappyPath_validation_cancel_wrongPassword_andCaseNormalization() {
        val email = E2eTestHarness.uniqueEmail("auth_flow")
        val password = "StrongPassword1!"

        // Edge case: Cancel the email dialog cleanly
        composeRule.waitUntilNodeExists(hasText(LOGIN_SCREEN_MARKER))
        composeRule.onNodeWithText(LOGIN_SCREEN_MARKER).performClick()
        composeRule.waitUntilNodeExists(hasTestTag("login_submit_button"))
        composeRule.onNodeWithTag("login_submit_button").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasTestTag("login_submit_button"))
        composeRule.onNodeWithText("Login Error").assertDoesNotExist()

        // Edge case: Whitespace-only email/password keeps the submit button disabled
        composeRule.onNodeWithText(LOGIN_SCREEN_MARKER).performClick()
        composeRule.waitUntilNodeExists(hasTestTag("login_email_input"))
        composeRule.onNodeWithTag("login_email_input").performTextInput("   ")
        composeRule.onNodeWithTag("login_password_input").performTextInput("   ")
        composeRule.onNodeWithTag("login_submit_button").assertIsNotEnabled()

        // Edge case: Signing in with an unknown email does NOT silently create an account
        composeRule.onNodeWithTag("login_email_input").performTextClearance()
        composeRule.onNodeWithTag("login_email_input").performTextInput(email)
        composeRule.onNodeWithTag("login_password_input").performTextClearance()
        composeRule.onNodeWithTag("login_password_input").performTextInput(password)
        composeRule.onNodeWithTag("login_submit_button").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("login_error_text"))
        composeRule.onNodeWithText("Invalid email or password").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        // Happy path: Explicit registration from the in-app form
        composeRule.registerWithEmailUi(email, password)

        // Sign out via Settings
        composeRule.openSettingsFromTopBar()
        composeRule.onNodeWithText("Sign Out").performScrollTo().performClick()
        composeRule.waitUntilNodeExists(hasText(LOGIN_SCREEN_MARKER))

        // Edge case: Registering the same email again is rejected
        composeRule.onNodeWithText(LOGIN_SCREEN_MARKER).performClick()
        composeRule.waitUntilNodeExists(hasTestTag("login_mode_toggle"))
        composeRule.onNodeWithTag("login_mode_toggle").performClick()
        composeRule.onNodeWithTag("login_email_input").performTextInput(email)
        composeRule.onNodeWithTag("login_password_input").performTextInput(password)
        composeRule.onNodeWithTag("register_confirm_password_input").performTextInput(password)
        composeRule.onNodeWithTag("login_submit_button").performClick()
        composeRule.waitUntilNodeExists(hasText("Email already registered"))
        composeRule.onNodeWithText("Cancel").performClick()

        // Edge case: Wrong password shows the generic (non-enumerating) error inside the dialog
        composeRule.onNodeWithText(LOGIN_SCREEN_MARKER).performClick()
        composeRule.waitUntilNodeExists(hasTestTag("login_email_input"))
        composeRule.onNodeWithTag("login_email_input").performTextInput(email)
        composeRule.onNodeWithTag("login_password_input").performTextInput("WrongPass999!")
        composeRule.onNodeWithTag("login_submit_button").performClick()
        composeRule.waitUntilNodeExists(hasText("Invalid email or password"))
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasTestTag("login_email_input"))

        // Happy path: Returning login normalizes uppercase and surrounding whitespace
        composeRule.signInWithEmailUi("  ${email.uppercase()}  ", password)
        composeRule.onNodeWithText("Start Focus").assertIsDisplayed()
    }

    @Test
    fun googleSignIn_succeedsAndLandsOnHome() {
        composeRule.waitUntilNodeExists(hasText("Sign in with Google"))
        composeRule.onNodeWithText("Sign in with Google").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        composeRule.onNodeWithText("Start Focus").assertIsDisplayed()
    }

    @Test
    fun settingsDisplayNameUpdate_cancelAndBlankRejected_versionAndTopBarShortcut() {
        val email = E2eTestHarness.uniqueEmail("profile_name")
        composeRule.signInWithEmailUi(email, "CozyPass123!")

        // Edge case: Top-bar leaf capsule shortcut navigates directly from Home to Oasis Shop
        composeRule.waitUntilNodeExists(hasTestTag("top_bar_leaves"))
        composeRule.onNodeWithTag("top_bar_leaves").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Supporter"))
        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))

        composeRule.openSettingsFromTopBar()

        // Verify version info renders accurately
        composeRule.onNodeWithText("1.1.0 (9)").performScrollTo().assertIsDisplayed()

        // Happy path: Update display name to SageFocus
        composeRule.onNodeWithText("Display Name").performScrollTo().performClick()
        composeRule.waitUntilNodeExists(hasTestTag("settings_username_input"))
        composeRule.onNodeWithTag("settings_username_input").performTextClearance()
        composeRule.onNodeWithTag("settings_username_input").performTextInput("SageFocus")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntilNodeExists(hasText("SageFocus"))

        // Edge case: Editing then tapping Cancel discards unsaved edits
        composeRule.onNodeWithText("Display Name").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("settings_username_input"))
        composeRule.onNodeWithTag("settings_username_input").performTextClearance()
        composeRule.onNodeWithTag("settings_username_input").performTextInput("DiscardedName")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Edit Username"))
        composeRule.onNodeWithText("SageFocus").assertIsDisplayed()

        // Edge case: Blank / whitespace display name is ignored and keeps "SageFocus"
        composeRule.onNodeWithText("Display Name").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("settings_username_input"))
        composeRule.onNodeWithTag("settings_username_input").performTextClearance()
        composeRule.onNodeWithTag("settings_username_input").performTextInput("    ")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Edit Username"))
        composeRule.onNodeWithText("SageFocus").assertIsDisplayed()

        // Return to Home and verify Journey reflects updated display name
        composeRule.onNodeWithContentDescription("OK").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        composeRule.openOasisSubTab("Journey")
        composeRule.waitUntilNodeExists(hasText("SageFocus's Oasis"))
    }

    @Test
    fun signOutClearsLocalUnfinishedTasks_andAccountDeletionRemovesAccount() {
        val email = E2eTestHarness.uniqueEmail("lifecycle")
        val initialPass = "FirstPass123!"
        val newPassAfterDelete = "BrandNewPass456!"

        composeRule.signInWithEmailUi(email, initialPass)

        // Add an unfinished task in Focus Room, then leave
        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.addTaskInFocusRoom("Task wiped on sign out")
        composeRule.waitUntilNodeExists(hasText("Task wiped on sign out"))

        composeRule.onNodeWithContentDescription("Leave").performClick()
        composeRule.waitUntilNodeExists(hasText("Continue"))
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))

        // Sign out and sign back in: unfinished task must have been wiped
        composeRule.openSettingsFromTopBar()
        composeRule.onNodeWithText("Sign Out").performScrollTo().performClick()
        composeRule.waitUntilNodeExists(hasText(LOGIN_SCREEN_MARKER))

        composeRule.signInWithEmailUi(email, initialPass)
        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("0/0 Done"))
        composeRule.onNodeWithText("Task wiped on sign out").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Leave").performClick()
        composeRule.waitUntilNodeExists(hasText("Continue"))
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))

        // Edge case: Cancel Delete Account keeps user signed in on Settings
        composeRule.openSettingsFromTopBar()
        composeRule.onNodeWithText("Delete Account").performScrollTo().performClick()
        composeRule.waitUntilNodeExists(hasText("Cancel"))
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Are you sure? This will permanently delete your stats, leaves, and friends list. This cannot be undone."))
        composeRule.onNodeWithText("Display Name").performScrollTo().assertIsDisplayed()

        // Happy path: Confirm Delete Account permanently deletes backend record
        composeRule.onNodeWithText("Delete Account").performScrollTo().performClick()
        composeRule.waitUntilNodeExists(hasText("Delete"))
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitUntilNodeExists(hasText(LOGIN_SCREEN_MARKER))

        // Re-registering the exact same email with a brand new password succeeds (proving cascade delete)
        composeRule.signInWithEmailUi(email, newPassAfterDelete)
        composeRule.onNodeWithText("Start Focus").assertIsDisplayed()
    }
}
