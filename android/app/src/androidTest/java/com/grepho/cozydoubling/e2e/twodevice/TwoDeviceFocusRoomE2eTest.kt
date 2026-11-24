package com.grepho.cozydoubling.e2e.twodevice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grepho.cozydoubling.core.MainActivity
import com.grepho.cozydoubling.e2e.AbortCoordinatorOnFailureRule
import com.grepho.cozydoubling.e2e.E2eCoordinator
import com.grepho.cozydoubling.e2e.E2eTestHarness
import com.grepho.cozydoubling.e2e.addTaskInFocusRoom
import com.grepho.cozydoubling.e2e.signInWithEmailUi
import com.grepho.cozydoubling.e2e.updateDisplayNameFromHome
import com.grepho.cozydoubling.e2e.waitUntilNodeDoesNotExist
import com.grepho.cozydoubling.e2e.waitUntilNodeExists
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real 2-device E2E scenarios covering E2E-04 (Focus Room real-time presence & sync),
 * rapid re-entry deduplication, same-account dual-device usage, and live display name updates.
 */
@RunWith(AndroidJUnit4::class)
class TwoDeviceFocusRoomE2eTest {

    @get:Rule
    val abortOnFailure = AbortCoordinatorOnFailureRule()

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

    // --- Scenario 1: Mutual presence, bidirectional task sync, leave, rapid re-entry dedup ---

    @Test
    fun twoDevices_mutualPresence_liveTaskSync_andRapidReentry_deviceA() {
        val emailA = E2eTestHarness.uniqueEmail("2d_room_a")
        composeRule.signInWithEmailUi(emailA, "CozyPass123!")
        composeRule.updateDisplayNameFromHome("AliceRoom")

        val bobUserId = E2eCoordinator.awaitValue("bob_user_id")
        E2eCoordinator.arrive("room1_ready", 2)

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))

        // Both devices see 2 focusing now and each other's card
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))
        composeRule.waitUntilNodeExists(hasText("BobRoom"))
        E2eCoordinator.arrive("room1_both_joined", 2)

        // Wait for Bob to update tasks via real UI -> Alice sees live update
        composeRule.waitUntilNodeExists(hasText("Polishing slides • 1/2 done"))
        E2eCoordinator.arrive("room1_bob_task_verified", 2)

        // Alice adds and completes a task -> Bob sees it live
        composeRule.addTaskInFocusRoom("Review notes")
        composeRule.waitUntilNodeExists(hasText("0/1 Done"))
        composeRule.onNodeWithTag("room_task_checkbox_Review notes").performClick()
        composeRule.waitUntilNodeExists(hasText("1/1 Done"))
        E2eCoordinator.arrive("room1_alice_task_verified", 2)

        // Bob leaves -> Alice drops to 1 focusing now and BobRoom disappears
        composeRule.waitUntilNodeDoesNotExist(hasText("BobRoom"))
        composeRule.waitUntilNodeExists(hasText("1 focusing now"))
        E2eCoordinator.arrive("room1_bob_left_verified", 2)

        // Bob immediately re-enters -> Alice sees 2 focusing now and exactly 1 card for Bob (no ghost duplicate)
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))
        composeRule.waitUntilNodeExists(hasText("BobRoom"))
        val bobCardCount = composeRule
            .onAllNodesWithTag("room_participant_card_$bobUserId")
            .fetchSemanticsNodes()
            .size
        assertEquals(1, bobCardCount)
        E2eCoordinator.arrive("room1_reentry_verified", 2)
    }

    @Test
    fun twoDevices_mutualPresence_liveTaskSync_andRapidReentry_deviceB() {
        val emailB = E2eTestHarness.uniqueEmail("2d_room_b")
        composeRule.signInWithEmailUi(emailB, "CozyPass123!")
        composeRule.updateDisplayNameFromHome("BobRoom")

        E2eCoordinator.publish("bob_user_id", E2eTestHarness.currentUserId())
        E2eCoordinator.arrive("room1_ready", 2)

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))
        composeRule.waitUntilNodeExists(hasText("AliceRoom"))
        E2eCoordinator.arrive("room1_both_joined", 2)

        // Bob adds 2 tasks, completes the first, switches active to the second
        composeRule.addTaskInFocusRoom("Drafting thesis")
        composeRule.addTaskInFocusRoom("Polishing slides")
        composeRule.waitUntilNodeExists(hasText("0/2 Done"))
        composeRule.onNodeWithTag("room_task_checkbox_Drafting thesis").performClick()
        composeRule.waitUntilNodeExists(hasText("1/2 Done"))
        composeRule.onNodeWithTag("room_task_row_Polishing slides").performClick()
        E2eCoordinator.arrive("room1_bob_task_verified", 2)

        // Bob waits for Alice's task progress update
        composeRule.waitUntilNodeExists(hasText("Review notes • 1/1 done"))
        E2eCoordinator.arrive("room1_alice_task_verified", 2)

        // Bob exits room to Summary -> Continue to Home
        composeRule.onNodeWithContentDescription("Leave").performClick()
        composeRule.waitUntilNodeExists(hasText("Continue"))
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        E2eCoordinator.arrive("room1_bob_left_verified", 2)

        // Bob immediately re-enters Focus Room
        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))
        composeRule.waitUntilNodeExists(hasText("AliceRoom"))
        E2eCoordinator.arrive("room1_reentry_verified", 2)
    }

    // --- Scenario 2: Same account logged into 2 real devices simultaneously ---

    @Test
    fun twoDevices_sameAccountSimultaneousRoom_noSelfDuplicate_andLeafSync_deviceA() {
        val sharedEmail = E2eTestHarness.uniqueEmail("shared_acct")
        val password = "SharedPassword123!"

        composeRule.signInWithEmailUi(sharedEmail, password)
        composeRule.updateDisplayNameFromHome("SharedUser")

        E2eCoordinator.publish("shared_email", sharedEmail)
        E2eCoordinator.publish("shared_password", password)
        E2eCoordinator.arrive("same_acct_logged_in", 2)

        // Both enter Focus Room at the same time with the exact same account
        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("1 focusing now"))
        E2eCoordinator.arrive("same_acct_both_in_room", 2)

        // Own duplicate self card must NOT be shown
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1 focusing now").assertIsDisplayed()
        assertEquals(
            1,
            composeRule.onAllNodesWithTag("room_participant_card_self").fetchSemanticsNodes().size
        )
        E2eCoordinator.arrive("same_acct_no_dup_checked", 2)

        // Device A completes 2 tasks and leaves (earns 6 leaves)
        composeRule.addTaskInFocusRoom("Shared Task One")
        composeRule.addTaskInFocusRoom("Shared Task Two")
        composeRule.waitUntilNodeExists(hasText("0/2 Done"))
        composeRule.onNodeWithTag("room_task_checkbox_Shared Task One").performClick()
        composeRule.onNodeWithTag("room_task_checkbox_Shared Task Two").performClick()
        composeRule.waitUntilNodeExists(hasText("2/2 Done"))

        composeRule.onNodeWithContentDescription("Leave").performClick()
        composeRule.waitUntilNodeExists(hasText("Continue"))
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        E2eCoordinator.arrive("same_acct_both_left", 2)

        // Switch Oasis -> Home to refresh latest backend balance (6 + 1 = 7 leaves)
        composeRule.onNodeWithText("Oasis").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Supporter"))
        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        composeRule.waitUntilNodeExists(hasText("7"))
    }

    @Test
    fun twoDevices_sameAccountSimultaneousRoom_noSelfDuplicate_andLeafSync_deviceB() {
        val sharedEmail = E2eCoordinator.awaitValue("shared_email")
        val password = E2eCoordinator.awaitValue("shared_password")

        composeRule.signInWithEmailUi(sharedEmail, password)
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        E2eCoordinator.arrive("same_acct_logged_in", 2)

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("1 focusing now"))
        E2eCoordinator.arrive("same_acct_both_in_room", 2)

        composeRule.waitForIdle()
        composeRule.onNodeWithText("1 focusing now").assertIsDisplayed()
        assertEquals(
            1,
            composeRule.onAllNodesWithTag("room_participant_card_self").fetchSemanticsNodes().size
        )
        E2eCoordinator.arrive("same_acct_no_dup_checked", 2)

        // Device B leaves with 0 tasks (+1 leaf floor -> total 7 once both leave)
        composeRule.onNodeWithContentDescription("Leave").performClick()
        composeRule.waitUntilNodeExists(hasText("Continue"))
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        E2eCoordinator.arrive("same_acct_both_left", 2)

        composeRule.onNodeWithText("Oasis").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Supporter"))
        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        composeRule.waitUntilNodeExists(hasText("7"))
    }

    // --- Scenario 3: Display name update reflected cleanly upon Focus Room re-entry ---

    @Test
    fun twoDevices_displayNameChangeReflectedOnReentry_deviceA() {
        val emailA = E2eTestHarness.uniqueEmail("2d_rename_a")
        composeRule.signInWithEmailUi(emailA, "CozyPass123!")
        composeRule.updateDisplayNameFromHome("AliceWatch")

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        E2eCoordinator.arrive("rename_alice_in_room", 2)

        composeRule.waitUntilNodeExists(hasText("BobRenamed"))
        E2eCoordinator.arrive("rename_first_verified", 2)

        composeRule.waitUntilNodeDoesNotExist(hasText("BobRenamed"))
        composeRule.waitUntilNodeExists(hasText("BobRenamedAgain"))
        E2eCoordinator.arrive("rename_second_verified", 2)
    }

    @Test
    fun twoDevices_displayNameChangeReflectedOnReentry_deviceB() {
        val emailB = E2eTestHarness.uniqueEmail("2d_rename_b")
        composeRule.signInWithEmailUi(emailB, "CozyPass123!")
        composeRule.updateDisplayNameFromHome("BobRenamed")
        E2eCoordinator.arrive("rename_alice_in_room", 2)

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("AliceWatch"))
        E2eCoordinator.arrive("rename_first_verified", 2)

        // Leave, update name again in Settings, and re-enter
        composeRule.onNodeWithContentDescription("Leave").performClick()
        composeRule.waitUntilNodeExists(hasText("Continue"))
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))

        composeRule.updateDisplayNameFromHome("BobRenamedAgain")
        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("AliceWatch"))
        E2eCoordinator.arrive("rename_second_verified", 2)
    }
}
