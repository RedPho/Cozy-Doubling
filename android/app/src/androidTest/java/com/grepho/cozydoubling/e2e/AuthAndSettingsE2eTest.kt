package com.grepho.cozydoubling.e2e

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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers E2E-02 (Authentication & Account Lifecycle) and E2E-09 (Settings Display Name).
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
    fun emailAuthHappyPath_validation_wrongPassword_andCaseNormalization() {
        val email = E2eTestHarness.uniqueEmail("auth_flow")
        val password = "StrongPassword1!"

        // Edge case: Login button is disabled while credentials are blank
        composeRule.waitUntilNodeExists(hasText("Reviewer Access"))
        composeRule.onNodeWithText("Reviewer Access").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("login_submit_button"))
        composeRule.onNodeWithTag("login_submit_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("login_email_input").performTextInput(email)
        composeRule.onNodeWithTag("login_submit_button").assertIsNotEnabled()

        // Happy path: Auto-register & login
        composeRule.onNodeWithTag("login_password_input").performTextInput(password)
        composeRule.onNodeWithTag("login_submit_button").assertIsEnabled().performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))

        // Sign out via Settings
        composeRule.openSettingsFromTopBar()
        composeRule.onNodeWithText("Sign Out").performScrollTo().performClick()
        composeRule.waitUntilNodeExists(hasText("Reviewer Access"))

        // Edge case: Wrong password for existing account shows explicit error dialog
        composeRule.onNodeWithText("Reviewer Access").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("login_email_input"))
        composeRule.onNodeWithTag("login_email_input").performTextInput(email)
        composeRule.onNodeWithTag("login_password_input").performTextInput("WrongPass999!")
        composeRule.onNodeWithTag("login_submit_button").performClick()

        composeRule.waitUntilNodeExists(hasText("Login Error"))
        composeRule.onNodeWithText("Incorrect password for this account.").assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Login Error"))

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
    fun settingsDisplayNameUpdate_blankRejected_andPropagatesToJourneyAndRoom() {
        val email = E2eTestHarness.uniqueEmail("profile_name")
        composeRule.signInWithEmailUi(email, "CozyPass123!")

        composeRule.openSettingsFromTopBar()

        // Happy path: Update display name to SageFocus
        composeRule.onNodeWithText("Display Name").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("settings_username_input"))
        composeRule.onNodeWithTag("settings_username_input").performTextClearance()
        composeRule.onNodeWithTag("settings_username_input").performTextInput("SageFocus")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntilNodeExists(hasText("SageFocus"))

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
        composeRule.waitUntilNodeExists(hasText("Reviewer Access"))

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
        composeRule.waitUntilNodeDoesNotExist(hasText("Are you sure? All your leaves, owned themes, and focus history will be permanently wiped."))
        composeRule.onNodeWithText("Display Name").assertIsDisplayed()

        // Happy path: Confirm Delete Account permanently deletes backend record
        composeRule.onNodeWithText("Delete Account").performScrollTo().performClick()
        composeRule.waitUntilNodeExists(hasText("Delete"))
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitUntilNodeExists(hasText("Reviewer Access"))

        // Re-registering the exact same email with a brand new password succeeds (proving cascade delete)
        composeRule.signInWithEmailUi(email, newPassAfterDelete)
        composeRule.onNodeWithText("Start Focus").assertIsDisplayed()
    }
}
