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
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grepho.cozydoubling.core.MainActivity
import com.grepho.cozydoubling.e2e.E2eTestHarness
import com.grepho.cozydoubling.e2e.addTaskInFocusRoom
import com.grepho.cozydoubling.e2e.openOasisSubTab
import com.grepho.cozydoubling.e2e.signInWithEmailUi
import com.grepho.cozydoubling.e2e.waitUntilNodeExists
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Single-device E2E covering E2E-03 (Focus Room Task CRUD & Local Persistence)
 * and E2E-05 (Session Completion, Leaf Economy & Cumulative Journey Stats).
 */
@RunWith(AndroidJUnit4::class)
class FocusRoomAndSummaryE2eTest {

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
    fun focusRoomEdgeCases_uncheckTask_deleteAllTasks_backHandler_andCumulativeJourney() {
        val email = E2eTestHarness.uniqueEmail("room_edge")
        composeRule.signInWithEmailUi(email, "CozyPass123!")

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))

        // Add 2 tasks and mark both complete (2/2 Done)
        composeRule.addTaskInFocusRoom("Task Alpha")
        composeRule.addTaskInFocusRoom("Task Beta")
        composeRule.waitUntilNodeExists(hasText("0/2 Done"))

        composeRule.onNodeWithTag("room_task_checkbox_Task Alpha").performClick()
        composeRule.onNodeWithTag("room_task_checkbox_Task Beta").performClick()
        composeRule.waitUntilNodeExists(hasText("2/2 Done"))
        composeRule.onNodeWithTag("room_task_delete_Task Alpha").assertDoesNotExist()

        // Uncheck Task Alpha -> delete icon reappears and becomes usable
        composeRule.onNodeWithTag("room_task_checkbox_Task Alpha").performClick()
        composeRule.waitUntilNodeExists(hasText("1/2 Done"))
        composeRule.waitUntilNodeExists(hasTestTag("room_task_delete_Task Alpha"))
        composeRule.onNodeWithTag("room_task_delete_Task Alpha").performClick()
        composeRule.waitUntilNodeExists(hasText("1/1 Done"))

        // Uncheck Task Beta and delete it too -> empty state falls back to "No active task • 0/0 done"
        composeRule.onNodeWithTag("room_task_checkbox_Task Beta").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("room_task_delete_Task Beta"))
        composeRule.onNodeWithTag("room_task_delete_Task Beta").performClick()
        composeRule.waitUntilNodeExists(hasText("0/0 Done"))
        composeRule.waitUntilNodeExists(hasText("No active task • 0/0 done"))

        // Add 1 task, complete it, and exit via Android hardware Back button (BackHandler)
        composeRule.addTaskInFocusRoom("Back button task")
        composeRule.waitUntilNodeExists(hasText("0/1 Done"))
        composeRule.onNodeWithTag("room_task_checkbox_Back button task").performClick()
        composeRule.waitUntilNodeExists(hasText("1/1 Done"))

        // Close IME soft keyboard first so pressBack reaches FocusRoomScreen's BackHandler
        Espresso.closeSoftKeyboard()
        composeRule.waitForIdle()
        Espresso.pressBack()
        composeRule.waitUntilNodeExists(hasText("Great job showing up today!"))
        composeRule.waitUntilNodeExists(hasText("1 tasks"))
        composeRule.waitUntilNodeExists(hasTestTag("summary_leaves_value") and hasText("3"))
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        composeRule.waitUntilNodeExists(hasText("3"))

        // Complete a 2nd session with 2 tasks (+6 leaves -> cumulative 9)
        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.addTaskInFocusRoom("Session 2 One")
        composeRule.addTaskInFocusRoom("Session 2 Two")
        composeRule.waitUntilNodeExists(hasText("0/2 Done"))
        composeRule.onNodeWithTag("room_task_checkbox_Session 2 One").performClick()
        composeRule.onNodeWithTag("room_task_checkbox_Session 2 Two").performClick()
        composeRule.waitUntilNodeExists(hasText("2/2 Done"))

        composeRule.onNodeWithContentDescription("Leave").performClick()
        composeRule.waitUntilNodeExists(hasText("Great job showing up today!"))
        composeRule.waitUntilNodeExists(hasTestTag("summary_leaves_value") and hasText("6"))
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        composeRule.waitUntilNodeExists(hasText("9"))

        // Journey dashboard displays cumulative total leaves
        composeRule.openOasisSubTab("Journey")
        composeRule.waitUntilNodeExists(hasText("Total Leaves"))
        composeRule.waitUntilNodeExists(hasText("9"))
    }
}
