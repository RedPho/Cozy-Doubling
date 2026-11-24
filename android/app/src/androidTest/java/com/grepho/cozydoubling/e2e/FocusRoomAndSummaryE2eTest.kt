package com.grepho.cozydoubling.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grepho.cozydoubling.core.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers E2E-03 (Focus Room Task CRUD & Persistence),
 * E2E-04 (Real-Time Multi-User Presence & Sync),
 * and E2E-05 (Session Completion, Leaf Economy & Minimum Floor).
 */
@RunWith(AndroidJUnit4::class)
class FocusRoomAndSummaryE2eTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null
    private val peers = mutableListOf<BackendPeerClient>()

    @Before
    fun setUp() {
        scenario = E2eTestHarness.launchMainActivity()
    }

    @After
    fun tearDown() {
        peers.forEach { it.close() }
        peers.clear()
        scenario?.close()
        E2eTestHarness.resetAppState()
    }

    @Test
    fun focusRoomTaskCrud_edgeCases_persistence_andRewardCalculation() {
        val email = E2eTestHarness.uniqueEmail("room_tasks")
        composeRule.signInWithEmailUi(email, "CozyPass123!")

        // Enter Focus Room
        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("1 focusing now"))
        composeRule.onNodeWithText("0/0 Done").assertIsDisplayed()

        // Edge case: Blank / whitespace task is ignored
        composeRule.onNodeWithTag("room_task_input").performTextInput("   ")
        composeRule.onNodeWithTag("room_add_task_button").performClick()
        composeRule.onNodeWithText("0/0 Done").assertIsDisplayed()

        // Add 3 tasks: first becomes active automatically
        composeRule.addTaskInFocusRoom("Write architecture docs")
        composeRule.waitUntilNodeExists(hasText("0/1 Done"))
        composeRule.waitUntilNodeExists(hasText("Write architecture docs • 0/1 done"))

        composeRule.addTaskInFocusRoom("Review PR feedback")
        composeRule.waitUntilNodeExists(hasText("0/2 Done"))

        composeRule.addTaskInFocusRoom("Keep unfinished task")
        composeRule.waitUntilNodeExists(hasText("0/3 Done"))

        // Switch active task by tapping row
        composeRule.onNodeWithTag("room_task_row_Review PR feedback").performClick()
        composeRule.waitUntilNodeExists(hasText("Review PR feedback • 0/3 done"))

        // Complete first task -> delete icon disappears for completed task
        composeRule.onNodeWithTag("room_task_checkbox_Write architecture docs").performClick()
        composeRule.waitUntilNodeExists(hasText("1/3 Done"))
        composeRule.onNodeWithTag("room_task_delete_Write architecture docs").assertDoesNotExist()

        // Delete unfinished active task -> removed & active task falls back cleanly
        composeRule.onNodeWithTag("room_task_delete_Review PR feedback").performClick()
        composeRule.waitUntilNodeExists(hasText("1/2 Done"))
        composeRule.onNodeWithTag("room_task_row_Review PR feedback").assertDoesNotExist()

        // Add and complete one more task -> 2 completed tasks + 1 unfinished task
        composeRule.addTaskInFocusRoom("Finish sprint checklist")
        composeRule.waitUntilNodeExists(hasText("1/3 Done"))
        composeRule.onNodeWithTag("room_task_checkbox_Finish sprint checklist").performClick()
        composeRule.waitUntilNodeExists(hasText("2/3 Done"))

        // Finish session -> Summary shows 2 tasks and 6 leaves (0 min * 1 + 2 tasks * 3 = 6)
        composeRule.onNodeWithContentDescription("Leave").performClick()
        composeRule.waitUntilNodeExists(hasText("Great job showing up today!"))
        composeRule.waitUntilNodeExists(hasText("2 tasks"))
        composeRule.waitUntilNodeExists(hasTestTag("summary_leaves_value") and hasText("6"))

        // Continue returns to Home with updated leaf capsule (6)
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        composeRule.waitUntilNodeExists(hasText("6"))

        // Re-enter Focus Room: only the unfinished task persists; completed tasks are gone
        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("0/1 Done"))
        composeRule.onNodeWithTag("room_task_row_Keep unfinished task").assertIsDisplayed()
        composeRule.onNodeWithTag("room_task_row_Write architecture docs").assertDoesNotExist()
        composeRule.onNodeWithTag("room_task_row_Finish sprint checklist").assertDoesNotExist()

        // Edge case: Leaving immediately with 0 completed tasks still awards minimum 1-leaf floor
        composeRule.onNodeWithContentDescription("Leave").performClick()
        composeRule.waitUntilNodeExists(hasText("Great job showing up today!"))
        composeRule.waitUntilNodeExists(hasText("0 tasks"))
        composeRule.waitUntilNodeExists(hasTestTag("summary_leaves_value") and hasText("1"))

        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        // Cumulative balance is now 6 + 1 = 7
        composeRule.waitUntilNodeExists(hasText("7"))
    }

    @Test
    fun focusRoomMultiUserRealtimePresence_taskUpdates_leave_andRapidReentryDedup() {
        val email = E2eTestHarness.uniqueEmail("room_multi")
        val peer = E2eTestHarness.createPeer("peer_osman", displayName = "OsmanPeer")
        peers.add(peer)

        composeRule.signInWithEmailUi(email, "CozyPass123!")
        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("1 focusing now"))

        // Peer joins live room -> appears in User A's grid immediately
        peer.joinRoom(activeTaskText = "Drafting thesis", completedTasks = 0, totalTasks = 2)
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))
        composeRule.waitUntilNodeExists(hasText("OsmanPeer"))
        composeRule.waitUntilNodeExists(hasText("Drafting thesis • 0/2 done"))

        // Peer updates active task & progress -> updates live on User A's screen
        peer.updateRoomPresence(activeTaskText = "Polishing slides", completedTasks = 1, totalTasks = 2)
        composeRule.waitUntilNodeExists(hasText("Polishing slides • 1/2 done"))

        // Edge case: Rapid peer re-entry with new session key produces no duplicate card
        peer.joinRoom(activeTaskText = "Reconnected task", completedTasks = 2, totalTasks = 2)
        composeRule.waitUntilNodeExists(hasText("Reconnected task • 2/2 done"))
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))
        val peerCardCount = composeRule
            .onAllNodesWithTag("room_participant_card_${peer.userId}")
            .fetchSemanticsNodes()
            .size
        assertEquals(1, peerCardCount)

        // Peer leaves room -> card disappears and count returns to 1
        peer.leaveRoom()
        composeRule.waitUntilNodeDoesNotExist(hasText("OsmanPeer"))
        composeRule.waitUntilNodeExists(hasText("1 focusing now"))
    }
}
