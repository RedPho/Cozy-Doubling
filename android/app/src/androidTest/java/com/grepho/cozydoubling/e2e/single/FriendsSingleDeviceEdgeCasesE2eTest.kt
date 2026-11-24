package com.grepho.cozydoubling.e2e.single

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grepho.cozydoubling.core.MainActivity
import com.grepho.cozydoubling.e2e.E2eTestHarness
import com.grepho.cozydoubling.e2e.openOasisSubTab
import com.grepho.cozydoubling.e2e.signInWithEmailUi
import com.grepho.cozydoubling.e2e.waitUntilNodeDoesNotExist
import com.grepho.cozydoubling.e2e.waitUntilNodeExists
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Single-device edge cases covering E2E-06 (Friends dialog guards)
 * and E2E-11 (Offline ConnectionErrorDialog & automatic network recovery).
 */
@RunWith(AndroidJUnit4::class)
class FriendsSingleDeviceEdgeCasesE2eTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        scenario = E2eTestHarness.launchMainActivity()
    }

    @After
    fun tearDown() {
        E2eTestHarness.runShellCommand("svc wifi enable")
        E2eTestHarness.runShellCommand("svc data enable")
        scenario?.close()
        E2eTestHarness.resetAppState()
    }

    @Test
    fun friendsDialog_blankIgnored_andCancelResetsInputDraft() {
        val email = E2eTestHarness.uniqueEmail("friends_dialog_edge")
        composeRule.signInWithEmailUi(email, "CozyPass123!")
        composeRule.openOasisSubTab("Friends")
        composeRule.waitUntilNodeExists(hasText("YOUR CONNECT CODE"))

        // Edge case: Blank/whitespace friend code is ignored cleanly without snackbar
        composeRule.onNodeWithContentDescription("Add Friend").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("friends_add_input"))
        composeRule.onNodeWithTag("friends_add_input").performTextInput("   ")
        composeRule.onNodeWithText("Send Request").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasTestTag("friends_add_input"))
        composeRule.onNodeWithText("Friend request sent!").assertDoesNotExist()
        composeRule.onNodeWithText("User not found").assertDoesNotExist()

        // Edge case: Cancel clears unsaved friend code draft when re-opening dialog
        composeRule.onNodeWithContentDescription("Add Friend").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("friends_add_input"))
        composeRule.onNodeWithTag("friends_add_input").performTextInput("ABCD1234")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasTestTag("friends_add_input"))

        composeRule.onNodeWithContentDescription("Add Friend").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("friends_add_input"))
        composeRule.onNodeWithText("ABCD1234").assertDoesNotExist()
        composeRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun connectivityLoss_showsOfflineDialog_andRecoversAutomaticallyWhenOnlineRestored() {
        val email = E2eTestHarness.uniqueEmail("offline_recovery")
        composeRule.signInWithEmailUi(email, "CozyPass123!")

        try {
            // Turn off both Wi-Fi and mobile data -> ConnectivityObserver emits Offline
            E2eTestHarness.runShellCommand("svc wifi disable")
            E2eTestHarness.runShellCommand("svc data disable")

            composeRule.waitUntilNodeExists(hasText("No Internet"), timeoutMs = 20_000L)
            composeRule.onNodeWithText("Retry Connection").assertIsDisplayed()
        } finally {
            // Always restore network interfaces before finishing
            E2eTestHarness.runShellCommand("svc data enable")
            E2eTestHarness.runShellCommand("svc wifi enable")
        }

        // Dialog automatically dismisses once ConnectivityObserver detects network restoration
        composeRule.waitUntilNodeDoesNotExist(hasText("No Internet"), timeoutMs = 25_000L)
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
    }
}
