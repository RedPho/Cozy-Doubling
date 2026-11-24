package com.grepho.cozydoubling.e2e.twodevice

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grepho.cozydoubling.core.MainActivity
import com.grepho.cozydoubling.e2e.AbortCoordinatorOnFailureRule
import com.grepho.cozydoubling.e2e.E2eCoordinator
import com.grepho.cozydoubling.e2e.E2eTestHarness
import com.grepho.cozydoubling.e2e.addTaskInFocusRoom
import com.grepho.cozydoubling.e2e.openOasisSubTab
import com.grepho.cozydoubling.e2e.openSettingsFromTopBar
import com.grepho.cozydoubling.e2e.readOwnConnectCodeInFriends
import com.grepho.cozydoubling.e2e.refreshFriendsTab
import com.grepho.cozydoubling.e2e.sendFriendRequestViaDialog
import com.grepho.cozydoubling.e2e.signInWithEmailUi
import com.grepho.cozydoubling.e2e.signOutFromHome
import com.grepho.cozydoubling.e2e.updateDisplayNameFromHome
import com.grepho.cozydoubling.e2e.waitUntilNodeDoesNotExist
import com.grepho.cozydoubling.e2e.waitUntilNodeExists
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real 2-device E2E scenarios covering E2E-06 (Friends & Social Activity),
 * E2E-09 (Safety Moderation & Bidirectional Real-Time Block Filtering),
 * and E2E-11 (Account Deletion Cascade Friend Removal).
 */
@RunWith(AndroidJUnit4::class)
class TwoDeviceFriendsAndSafetyE2eTest {

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

    // --- Scenario 1: Full Friends lifecycle on 2 real phones ---

    @Test
    fun twoDevices_friendsFullLifecycle_errors_reject_accept_alreadyFriends_andStory_deviceA() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("2d_fr_a"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("AliceFriend")

        composeRule.openOasisSubTab("Friends")
        val aliceTag = composeRule.readOwnConnectCodeInFriends()
        E2eCoordinator.publish("alice_tag", aliceTag)
        val bobTag = E2eCoordinator.awaitValue("bob_tag")
        E2eCoordinator.arrive("fr1_tags_ready", 2)

        // Edge case: Self-add rejected
        composeRule.sendFriendRequestViaDialog(aliceTag)
        composeRule.waitUntilNodeExists(hasText("You can't add yourself!", substring = true))
        composeRule.waitUntilNodeDoesNotExist(hasText("You can't add yourself!", substring = true))

        // Edge case: Unknown player tag rejected
        composeRule.sendFriendRequestViaDialog("ZZZZ9999")
        composeRule.waitUntilNodeExists(hasText("User not found", substring = true))
        composeRule.waitUntilNodeDoesNotExist(hasText("User not found", substring = true))

        // Happy path: Send request using lowercase + '#' normalization
        composeRule.sendFriendRequestViaDialog("#${bobTag.lowercase()}")
        composeRule.waitUntilNodeExists(hasText("Friend request sent!"))
        composeRule.waitUntilNodeDoesNotExist(hasText("Friend request sent!"))

        // Edge case: Duplicate request rejected
        composeRule.sendFriendRequestViaDialog(bobTag)
        composeRule.waitUntilNodeExists(hasText("Request already sent!", substring = true))
        composeRule.waitUntilNodeDoesNotExist(hasText("Request already sent!", substring = true))
        E2eCoordinator.arrive("fr1_sent_first", 2)

        // Wait for Bob to reject
        E2eCoordinator.arrive("fr1_bob_rejected", 2)

        // Re-send request so Bob can accept it
        composeRule.sendFriendRequestViaDialog(bobTag)
        composeRule.waitUntilNodeExists(hasText("Friend request sent!"))
        composeRule.waitUntilNodeDoesNotExist(hasText("Friend request sent!"))
        E2eCoordinator.arrive("fr1_sent_second", 2)

        // Wait for Bob to accept -> refresh and verify Neighbourhood + no-session story
        E2eCoordinator.arrive("fr1_bob_accepted", 2)
        composeRule.refreshFriendsTab()
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (1)"))
        composeRule.waitUntilNodeExists(hasText("BobFriend #$bobTag"))
        composeRule.waitUntilNodeExists(hasText("Just joined! No sessions yet."))

        // Edge case: Sending request to existing friend shows "Already friends!"
        composeRule.sendFriendRequestViaDialog(bobTag)
        composeRule.waitUntilNodeExists(hasText("Already friends!", substring = true))
        composeRule.waitUntilNodeDoesNotExist(hasText("Already friends!", substring = true))
        E2eCoordinator.arrive("fr1_already_friends_verified", 2)

        // Wait for Bob to complete a real Focus Room session on Device B -> verify story appears
        E2eCoordinator.arrive("fr1_bob_session_done", 2)
        composeRule.refreshFriendsTab()
        composeRule.waitUntilNodeExists(hasText("Study algorithms", substring = true))
    }

    @Test
    fun twoDevices_friendsFullLifecycle_errors_reject_accept_alreadyFriends_andStory_deviceB() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("2d_fr_b"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("BobFriend")

        composeRule.openOasisSubTab("Friends")
        val bobTag = composeRule.readOwnConnectCodeInFriends()
        E2eCoordinator.publish("bob_tag", bobTag)
        val aliceTag = E2eCoordinator.awaitValue("alice_tag")
        E2eCoordinator.arrive("fr1_tags_ready", 2)

        // Wait for Alice's initial request and reject it
        E2eCoordinator.arrive("fr1_sent_first", 2)
        composeRule.refreshFriendsTab()
        composeRule.waitUntilNodeExists(hasText("Pending Requests (1)"))
        composeRule.waitUntilNodeExists(hasText("AliceFriend"))
        composeRule.onNodeWithTag("pending_reject_$aliceTag").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Pending Requests (1)"))
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (0)"))
        E2eCoordinator.arrive("fr1_bob_rejected", 2)

        // Wait for Alice's second request and accept it
        E2eCoordinator.arrive("fr1_sent_second", 2)
        composeRule.refreshFriendsTab()
        composeRule.waitUntilNodeExists(hasText("Pending Requests (1)"))
        composeRule.onNodeWithTag("pending_accept_$aliceTag").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Pending Requests (1)"))
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (1)"))
        composeRule.waitUntilNodeExists(hasText("AliceFriend #$aliceTag"))
        composeRule.waitUntilNodeExists(hasText("Just joined! No sessions yet."))
        E2eCoordinator.arrive("fr1_bob_accepted", 2)

        E2eCoordinator.arrive("fr1_already_friends_verified", 2)

        // Complete a real Focus Room session on Device B so Alice sees the story
        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))

        composeRule.addTaskInFocusRoom("Study algorithms")
        composeRule.waitUntilNodeExists(hasText("0/1 Done"))
        composeRule.onNodeWithTag("room_task_checkbox_Study algorithms").performClick()
        composeRule.waitUntilNodeExists(hasText("1/1 Done"))

        composeRule.onNodeWithContentDescription("Leave").performClick()
        composeRule.waitUntilNodeExists(hasText("Continue"))
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        E2eCoordinator.arrive("fr1_bob_session_done", 2)
    }

    // --- Scenario 2: Mutual friend request auto-acceptance on 2 real phones ---

    @Test
    fun twoDevices_mutualRequestAutoAccept_deviceA() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("2d_mut_a"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("AliceMutual")

        composeRule.openOasisSubTab("Friends")
        val aliceTag = composeRule.readOwnConnectCodeInFriends()
        E2eCoordinator.publish("alice_tag", aliceTag)
        val bobTag = E2eCoordinator.awaitValue("bob_tag")
        E2eCoordinator.arrive("mut_tags_ready", 2)

        composeRule.sendFriendRequestViaDialog(bobTag)
        composeRule.waitUntilNodeExists(hasText("Friend request sent!"))
        composeRule.waitUntilNodeDoesNotExist(hasText("Friend request sent!"))
        E2eCoordinator.arrive("mut_alice_sent", 2)

        // Wait for Bob to send mutual request back without clicking Accept
        E2eCoordinator.arrive("mut_bob_auto_accepted", 2)
        composeRule.refreshFriendsTab()
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (1)"))
        composeRule.waitUntilNodeExists(hasText("BobMutual #$bobTag"))
    }

    @Test
    fun twoDevices_mutualRequestAutoAccept_deviceB() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("2d_mut_b"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("BobMutual")

        composeRule.openOasisSubTab("Friends")
        val bobTag = composeRule.readOwnConnectCodeInFriends()
        E2eCoordinator.publish("bob_tag", bobTag)
        val aliceTag = E2eCoordinator.awaitValue("alice_tag")
        E2eCoordinator.arrive("mut_tags_ready", 2)

        E2eCoordinator.arrive("mut_alice_sent", 2)

        // Bob sends a friend request back by tag instead of clicking Accept -> auto-accepted!
        composeRule.sendFriendRequestViaDialog(aliceTag)
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (1)"))
        composeRule.waitUntilNodeExists(hasText("AliceMutual #$aliceTag"))
        E2eCoordinator.arrive("mut_bob_auto_accepted", 2)
    }

    // --- Scenario 3: Report, cancel, bidirectional real-time block in Friends & Focus Room ---

    @Test
    fun twoDevices_safetyReportAndBlock_friendsAndRoom_bidirectionalRealtime_deviceA() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("2d_safe_a1"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("AliceSafe")

        composeRule.openOasisSubTab("Friends")
        val aliceTag = composeRule.readOwnConnectCodeInFriends()
        val bobId = E2eCoordinator.awaitValue("bob_id")
        val bobTag = E2eCoordinator.awaitValue("bob_tag")
        E2eCoordinator.publish("alice_tag", aliceTag)
        E2eCoordinator.arrive("safe_p1_tags", 2)

        // Wait for Bob's request & accept
        E2eCoordinator.arrive("safe_p1_req_sent", 2)
        composeRule.refreshFriendsTab()
        composeRule.waitUntilNodeExists(hasText("Pending Requests (1)"))
        composeRule.onNodeWithTag("pending_accept_$bobTag").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Pending Requests (1)"))
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (1)"))
        composeRule.waitUntilNodeExists(hasTestTag("friends_card_$bobId"))
        composeRule.waitForIdle()

        // Edge case: Open Report dialog and Cancel
        composeRule.onNodeWithTag("friends_more_$bobId").performClick()
        composeRule.waitUntilNodeExists(hasText("Report User"))
        composeRule.onNodeWithText("Report User").performClick()
        composeRule.waitUntilNodeExists(hasText("Inappropriate Name"))
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Inappropriate Name"))

        // Happy path: Submit report
        composeRule.onNodeWithTag("friends_more_$bobId").performClick()
        composeRule.waitUntilNodeExists(hasText("Report User"))
        composeRule.onNodeWithText("Report User").performClick()
        composeRule.waitUntilNodeExists(hasText("Inappropriate Name"))
        composeRule.onNodeWithText("Inappropriate Name").performClick()
        composeRule.waitUntilNodeExists(hasText("Report submitted. Thank you."))
        composeRule.waitUntilNodeDoesNotExist(hasText("Report submitted. Thank you."))

        // Block Bob from Friends -> disappears on BOTH Device A and Device B in real time!
        composeRule.onNodeWithTag("friends_more_$bobId").performClick()
        composeRule.waitUntilNodeExists(hasText("Block User"))
        composeRule.onNodeWithText("Block User").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasTestTag("friends_card_$bobId"))
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (0)"))
        E2eCoordinator.arrive("safe_p1_blocked_verified", 2)

        // Both enter Focus Room -> blocked peer must remain hidden
        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        E2eCoordinator.arrive("safe_p1_both_in_room", 2)

        composeRule.waitForIdle()
        composeRule.onNodeWithText("BobSafe").assertDoesNotExist()
        composeRule.onNodeWithText("1 focusing now").assertExists()
        E2eCoordinator.arrive("safe_p1_room_hidden", 2)

        // Exit Focus Room and verify Bob's post-block friend request stays suppressed
        composeRule.onNodeWithContentDescription("Leave").performClick()
        composeRule.waitUntilNodeExists(hasText("Continue"))
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))

        E2eCoordinator.arrive("safe_p1_bob_sent_post_block_req", 2)
        composeRule.openOasisSubTab("Friends")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pending Requests (1)").assertDoesNotExist()
        E2eCoordinator.arrive("safe_p1_suppressed_verified", 2)

        // Part 2: Fresh accounts enter Focus Room together -> Alice blocks Bob live in Focus Room!
        composeRule.onNodeWithText("Home").performClick()
        composeRule.signOutFromHome()
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("2d_safe_a2"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("AliceRoomBlk")

        val bob2Id = E2eCoordinator.awaitValue("bob2_id")
        E2eCoordinator.arrive("safe_p2_ready", 2)

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))
        composeRule.waitUntilNodeExists(hasText("BobRoomBlk"))
        E2eCoordinator.arrive("safe_p2_both_in_room", 2)

        // Block Bob directly from Focus Room card -> disappears immediately on BOTH devices
        composeRule.onNodeWithTag("room_participant_options_$bob2Id").performClick()
        composeRule.waitUntilNodeExists(hasText("Block User"))
        composeRule.onNodeWithText("Block User").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("BobRoomBlk"))
        composeRule.waitUntilNodeExists(hasText("1 focusing now"))
        E2eCoordinator.arrive("safe_p2_room_block_verified", 2)
    }

    @Test
    fun twoDevices_safetyReportAndBlock_friendsAndRoom_bidirectionalRealtime_deviceB() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("2d_safe_b1"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("BobSafe")

        composeRule.openOasisSubTab("Friends")
        val bobTag = composeRule.readOwnConnectCodeInFriends()
        E2eCoordinator.publish("bob_id", E2eTestHarness.currentUserId())
        E2eCoordinator.publish("bob_tag", bobTag)
        val aliceTag = E2eCoordinator.awaitValue("alice_tag")
        E2eCoordinator.arrive("safe_p1_tags", 2)

        composeRule.sendFriendRequestViaDialog(aliceTag)
        composeRule.waitUntilNodeExists(hasText("Friend request sent!"))
        composeRule.waitUntilNodeDoesNotExist(hasText("Friend request sent!"))
        E2eCoordinator.arrive("safe_p1_req_sent", 2)

        // Wait on FriendsScreen: as soon as Alice blocks Bob, Bob sees Neighbourhood drop to (0) live!
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (0)"), timeoutMs = 30_000L)
        composeRule.onNodeWithText("AliceSafe").assertDoesNotExist()
        E2eCoordinator.arrive("safe_p1_blocked_verified", 2)

        // Both enter Focus Room -> Bob stays hidden from Alice
        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        E2eCoordinator.arrive("safe_p1_both_in_room", 2)

        composeRule.waitForIdle()
        composeRule.onNodeWithText("AliceSafe").assertDoesNotExist()
        composeRule.onNodeWithText("1 focusing now").assertExists()
        E2eCoordinator.arrive("safe_p1_room_hidden", 2)

        // Exit Focus Room and attempt post-block friend request to Alice
        composeRule.onNodeWithContentDescription("Leave").performClick()
        composeRule.waitUntilNodeExists(hasText("Continue"))
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))

        composeRule.openOasisSubTab("Friends")
        composeRule.sendFriendRequestViaDialog(aliceTag)
        composeRule.waitUntilNodeExists(hasText("Friend request sent!"))
        composeRule.waitUntilNodeDoesNotExist(hasText("Friend request sent!"))
        E2eCoordinator.arrive("safe_p1_bob_sent_post_block_req", 2)
        E2eCoordinator.arrive("safe_p1_suppressed_verified", 2)

        // Part 2: Fresh account enters Focus Room -> Alice blocks Bob inside Focus Room -> Bob sees Alice disappear live!
        composeRule.onNodeWithText("Home").performClick()
        composeRule.signOutFromHome()
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("2d_safe_b2"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("BobRoomBlk")

        E2eCoordinator.publish("bob2_id", E2eTestHarness.currentUserId())
        E2eCoordinator.arrive("safe_p2_ready", 2)

        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))
        composeRule.waitUntilNodeExists(hasText("AliceRoomBlk"))
        E2eCoordinator.arrive("safe_p2_both_in_room", 2)

        // Real-time /ws/safety + room_manager removal when Alice blocks Bob
        composeRule.waitUntilNodeDoesNotExist(hasText("AliceRoomBlk"), timeoutMs = 25_000L)
        composeRule.waitUntilNodeExists(hasText("1 focusing now"))
        E2eCoordinator.arrive("safe_p2_room_block_verified", 2)
    }

    // --- Scenario 4: Deleting account on Device B cascades friendship removal on Device A ---

    @Test
    fun twoDevices_accountDeletionCascadesFriendRemoval_deviceA() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("2d_del_a"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("AliceKeeper")

        composeRule.openOasisSubTab("Friends")
        val aliceTag = composeRule.readOwnConnectCodeInFriends()
        val bobTag = E2eCoordinator.awaitValue("del_bob_tag")
        E2eCoordinator.publish("del_alice_tag", aliceTag)
        E2eCoordinator.arrive("del_tags_ready", 2)

        // Send request and wait for Bob to accept
        composeRule.sendFriendRequestViaDialog(bobTag)
        composeRule.waitUntilNodeExists(hasText("Friend request sent!"))
        composeRule.waitUntilNodeDoesNotExist(hasText("Friend request sent!"))
        E2eCoordinator.arrive("del_req_sent", 2)
        E2eCoordinator.arrive("del_accepted", 2)

        composeRule.refreshFriendsTab()
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (1)"))
        composeRule.waitUntilNodeExists(hasText("BobDeleted #$bobTag"))
        E2eCoordinator.arrive("del_friendship_confirmed", 2)

        // Wait for Bob to permanently delete account on Device B
        E2eCoordinator.arrive("del_bob_deleted", 2)
        composeRule.refreshFriendsTab()
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (0)"))
        composeRule.onNodeWithText("BobDeleted").assertDoesNotExist()
    }

    @Test
    fun twoDevices_accountDeletionCascadesFriendRemoval_deviceB() {
        composeRule.signInWithEmailUi(E2eTestHarness.uniqueEmail("2d_del_b"), "CozyPass123!")
        composeRule.updateDisplayNameFromHome("BobDeleted")

        composeRule.openOasisSubTab("Friends")
        val bobTag = composeRule.readOwnConnectCodeInFriends()
        E2eCoordinator.publish("del_bob_tag", bobTag)
        val aliceTag = E2eCoordinator.awaitValue("del_alice_tag")
        E2eCoordinator.arrive("del_tags_ready", 2)

        E2eCoordinator.arrive("del_req_sent", 2)
        composeRule.refreshFriendsTab()
        composeRule.waitUntilNodeExists(hasText("Pending Requests (1)"))
        composeRule.onNodeWithTag("pending_accept_$aliceTag").performClick()
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (1)"))
        E2eCoordinator.arrive("del_accepted", 2)

        E2eCoordinator.arrive("del_friendship_confirmed", 2)

        // Delete Account via Settings UI
        composeRule.onNodeWithText("Home").performClick()
        composeRule.openSettingsFromTopBar()
        composeRule.onNodeWithText("Delete Account").performScrollTo().performClick()
        composeRule.waitUntilNodeExists(hasText("Delete"))
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitUntilNodeExists(hasText("Reviewer Access"))
        E2eCoordinator.arrive("del_bob_deleted", 2)
    }
}
