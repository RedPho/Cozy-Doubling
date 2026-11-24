package com.grepho.cozydoubling.e2e.threedevice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
import com.grepho.cozydoubling.e2e.openOasisSubTab
import com.grepho.cozydoubling.e2e.readOwnConnectCodeInFriends
import com.grepho.cozydoubling.e2e.refreshFriendsTab
import com.grepho.cozydoubling.e2e.sendFriendRequestViaDialog
import com.grepho.cozydoubling.e2e.signInWithEmailUi
import com.grepho.cozydoubling.e2e.updateDisplayNameFromHome
import com.grepho.cozydoubling.e2e.waitUntilNodeDoesNotExist
import com.grepho.cozydoubling.e2e.waitUntilNodeExists
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real 3-device E2E scenarios covering E2E-10:
 * - 3 simultaneous users in Focus Room + live task sync + selective leave
 * - Asymmetric 3-user block isolation (A & B hide each other while C sees both)
 * - Multiple simultaneous incoming friend requests (accept one, reject other)
 */
@RunWith(AndroidJUnit4::class)
class ThreeDeviceE2eTest {

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

    // --- Triple 1: 3 users in Focus Room, live task updates across all 3, selective leave ---

    @Test
    fun threeDevices_simultaneousRoomPresence_liveTaskUpdates_andSelectiveLeave_deviceA() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("3d_room_a"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("Alice3")
        E2eCoordinator.arrive("3room_ready", 3)

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("3 focusing now"))
        composeRule.waitUntilNodeExists(hasText("Bob3"))
        composeRule.waitUntilNodeExists(hasText("Charlie3"))
        E2eCoordinator.arrive("3room_all_joined", 3)

        // Verify live updates from both Bob and Charlie
        composeRule.waitUntilNodeExists(hasText("Bob writing code • 1/1 done"))
        composeRule.waitUntilNodeExists(hasText("Charlie reading • 0/2 done"))
        E2eCoordinator.arrive("3room_tasks_verified", 3)

        // Charlie leaves -> Alice and Bob remain together (2 focusing now)
        composeRule.waitUntilNodeDoesNotExist(hasText("Charlie3"))
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))
        composeRule.waitUntilNodeExists(hasText("Bob3"))
        E2eCoordinator.arrive("3room_charlie_left_verified", 3)
    }

    @Test
    fun threeDevices_simultaneousRoomPresence_liveTaskUpdates_andSelectiveLeave_deviceB() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("3d_room_b"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("Bob3")
        E2eCoordinator.arrive("3room_ready", 3)

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("3 focusing now"))
        composeRule.waitUntilNodeExists(hasText("Alice3"))
        composeRule.waitUntilNodeExists(hasText("Charlie3"))
        E2eCoordinator.arrive("3room_all_joined", 3)

        // Bob adds and completes a task -> Alice and Charlie see it live
        composeRule.addTaskInFocusRoom("Bob writing code")
        composeRule.waitUntilNodeExists(hasText("0/1 Done"))
        composeRule.onNodeWithTag("room_task_checkbox_Bob writing code").performClick()
        composeRule.waitUntilNodeExists(hasText("1/1 Done"))

        composeRule.waitUntilNodeExists(hasText("Charlie reading • 0/2 done"))
        E2eCoordinator.arrive("3room_tasks_verified", 3)

        composeRule.waitUntilNodeDoesNotExist(hasText("Charlie3"))
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))
        composeRule.waitUntilNodeExists(hasText("Alice3"))
        E2eCoordinator.arrive("3room_charlie_left_verified", 3)
    }

    @Test
    fun threeDevices_simultaneousRoomPresence_liveTaskUpdates_andSelectiveLeave_deviceC() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("3d_room_c"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("Charlie3")
        E2eCoordinator.arrive("3room_ready", 3)

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("3 focusing now"))
        composeRule.waitUntilNodeExists(hasText("Alice3"))
        composeRule.waitUntilNodeExists(hasText("Bob3"))
        E2eCoordinator.arrive("3room_all_joined", 3)

        // Charlie adds 2 tasks -> Alice and Bob see "Charlie reading • 0/2 done"
        composeRule.addTaskInFocusRoom("Charlie reading")
        composeRule.addTaskInFocusRoom("Charlie reviewing")
        composeRule.waitUntilNodeExists(hasText("0/2 Done"))

        composeRule.waitUntilNodeExists(hasText("Bob writing code • 1/1 done"))
        E2eCoordinator.arrive("3room_tasks_verified", 3)

        // Selective leave by Charlie
        composeRule.onNodeWithContentDescription("Leave").performClick()
        composeRule.waitUntilNodeExists(hasText("Continue"))
        E2eCoordinator.arrive("3room_charlie_left_verified", 3)
    }

    // --- Triple 2: Asymmetric block isolation (A blocks B -> A & B hide each other, C still sees BOTH) ---

    @Test
    fun threeDevices_asymmetricBlockIsolation_roomAndFriends_deviceA() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("3d_iso_a"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("AliceIso")
        val bobId = E2eCoordinator.awaitValue("iso_bob_id")
        E2eCoordinator.arrive("iso_ready", 3)

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("3 focusing now"))
        composeRule.waitUntilNodeExists(hasText("BobIso"))
        composeRule.waitUntilNodeExists(hasText("CharlieIso"))
        E2eCoordinator.arrive("iso_all_in_room", 3)

        // Alice blocks Bob from Focus Room UI -> Alice hides Bob, keeps Charlie
        composeRule.onNodeWithTag("room_participant_options_$bobId").performClick()
        composeRule.waitUntilNodeExists(hasText("Block User"))
        composeRule.onNodeWithText("Block User").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("BobIso"))
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))
        composeRule.onNodeWithText("CharlieIso").assertIsDisplayed()
        E2eCoordinator.arrive("iso_block_applied", 3)

        // Bob updates task afterward -> Charlie sees it, Alice does NOT see Bob
        E2eCoordinator.arrive("iso_bob_post_block_verified", 3)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("BobIso").assertDoesNotExist()
        composeRule.onNodeWithText("2 focusing now").assertIsDisplayed()
        E2eCoordinator.arrive("iso_done", 3)
    }

    @Test
    fun threeDevices_asymmetricBlockIsolation_roomAndFriends_deviceB() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("3d_iso_b"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("BobIso")
        E2eCoordinator.publish("iso_bob_id", E2eTestHarness.currentUserId())
        E2eCoordinator.arrive("iso_ready", 3)

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("3 focusing now"))
        composeRule.waitUntilNodeExists(hasText("AliceIso"))
        composeRule.waitUntilNodeExists(hasText("CharlieIso"))
        E2eCoordinator.arrive("iso_all_in_room", 3)

        // Wait for Alice to block Bob -> Bob loses Alice in real time, keeps Charlie!
        composeRule.waitUntilNodeDoesNotExist(hasText("AliceIso"), timeoutMs = 25_000L)
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))
        composeRule.onNodeWithText("CharlieIso").assertIsDisplayed()
        E2eCoordinator.arrive("iso_block_applied", 3)

        // Bob broadcasts a task update -> Charlie sees it live, Alice stays isolated
        composeRule.addTaskInFocusRoom("Bob post-block task")
        composeRule.waitUntilNodeExists(hasText("0/1 Done"))
        E2eCoordinator.arrive("iso_bob_post_block_verified", 3)
        E2eCoordinator.arrive("iso_done", 3)
    }

    @Test
    fun threeDevices_asymmetricBlockIsolation_roomAndFriends_deviceC() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("3d_iso_c"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("CharlieIso")
        E2eCoordinator.arrive("iso_ready", 3)

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("3 focusing now"))
        composeRule.waitUntilNodeExists(hasText("AliceIso"))
        composeRule.waitUntilNodeExists(hasText("BobIso"))
        E2eCoordinator.arrive("iso_all_in_room", 3)

        // After A blocks B, Charlie MUST still see BOTH AliceIso and BobIso (3 focusing now)
        E2eCoordinator.arrive("iso_block_applied", 3)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("3 focusing now").assertIsDisplayed()
        composeRule.onNodeWithText("AliceIso").assertIsDisplayed()
        composeRule.onNodeWithText("BobIso").assertIsDisplayed()

        // Charlie receives Bob's live task update even though A and B are blocked from each other
        composeRule.waitUntilNodeExists(hasText("Bob post-block task • 0/1 done"))
        composeRule.onNodeWithText("3 focusing now").assertIsDisplayed()
        composeRule.onNodeWithText("AliceIso").assertIsDisplayed()
        E2eCoordinator.arrive("iso_bob_post_block_verified", 3)
        E2eCoordinator.arrive("iso_done", 3)
    }

    // --- Triple 3: Multiple pending requests -> accept Bob, reject Charlie cleanly ---

    @Test
    fun threeDevices_multiplePendingRequests_acceptOneRejectOther_deviceA() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("3d_mix_a"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("AliceMix")

        composeRule.openOasisSubTab("Friends")
        val aliceTag = composeRule.readOwnConnectCodeInFriends()
        E2eCoordinator.publish("mix_alice_tag", aliceTag)
        val bobTag = E2eCoordinator.awaitValue("mix_bob_tag")
        val charlieTag = E2eCoordinator.awaitValue("mix_charlie_tag")
        E2eCoordinator.arrive("mix_reqs_sent", 3)

        composeRule.refreshFriendsTab()
        composeRule.waitUntilNodeExists(hasText("Pending Requests (2)"))
        composeRule.waitUntilNodeExists(hasTestTag("pending_accept_$bobTag"))
        composeRule.waitUntilNodeExists(hasTestTag("pending_reject_$charlieTag"))

        // Deterministically accept Bob and reject Charlie using per-tag buttons
        composeRule.onNodeWithTag("pending_accept_$bobTag").performClick()
        composeRule.waitUntilNodeExists(hasText("Pending Requests (1)"))
        composeRule.waitUntilNodeExists(hasTestTag("pending_reject_$charlieTag"))
        composeRule.onNodeWithTag("pending_reject_$charlieTag").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Pending Requests (1)"))

        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (1)"))
        composeRule.waitUntilNodeExists(hasText("BobMix #$bobTag"))
        composeRule.onNodeWithText("CharlieMix #$charlieTag").assertDoesNotExist()
        E2eCoordinator.arrive("mix_resolved", 3)
    }

    @Test
    fun threeDevices_multiplePendingRequests_acceptOneRejectOther_deviceB() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("3d_mix_b"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("BobMix")

        composeRule.openOasisSubTab("Friends")
        E2eCoordinator.publish("mix_bob_tag", composeRule.readOwnConnectCodeInFriends())
        val aliceTag = E2eCoordinator.awaitValue("mix_alice_tag")

        composeRule.sendFriendRequestViaDialog(aliceTag)
        composeRule.waitUntilNodeExists(hasText("Friend request sent!"))
        composeRule.waitUntilNodeDoesNotExist(hasText("Friend request sent!"))
        E2eCoordinator.arrive("mix_reqs_sent", 3)

        E2eCoordinator.arrive("mix_resolved", 3)
        composeRule.refreshFriendsTab()
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (1)"))
        composeRule.waitUntilNodeExists(hasText("AliceMix #$aliceTag"))
    }

    @Test
    fun threeDevices_multiplePendingRequests_acceptOneRejectOther_deviceC() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("3d_mix_c"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("CharlieMix")

        composeRule.openOasisSubTab("Friends")
        E2eCoordinator.publish("mix_charlie_tag", composeRule.readOwnConnectCodeInFriends())
        val aliceTag = E2eCoordinator.awaitValue("mix_alice_tag")

        composeRule.sendFriendRequestViaDialog(aliceTag)
        composeRule.waitUntilNodeExists(hasText("Friend request sent!"))
        composeRule.waitUntilNodeDoesNotExist(hasText("Friend request sent!"))
        E2eCoordinator.arrive("mix_reqs_sent", 3)

        E2eCoordinator.arrive("mix_resolved", 3)
        composeRule.refreshFriendsTab()
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (0)"))
        composeRule.onNodeWithText("AliceMix #$aliceTag").assertDoesNotExist()
    }
}
