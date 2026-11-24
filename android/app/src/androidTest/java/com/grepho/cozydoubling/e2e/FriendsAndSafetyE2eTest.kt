package com.grepho.cozydoubling.e2e

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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers E2E-06 (Friends & Social Activity) and E2E-09 (Safety Reporting & Real-Time Blocking).
 */
@RunWith(AndroidJUnit4::class)
class FriendsAndSafetyE2eTest {

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

    private fun sendFriendRequestViaDialog(tagInput: String) {
        composeRule.onNodeWithContentDescription("Add Friend").performClick()
        composeRule.waitUntilNodeExists(hasTestTag("friends_add_input"))
        composeRule.onNodeWithTag("friends_add_input").performTextInput(tagInput)
        composeRule.onNodeWithText("Send Request").performClick()
    }

    @Test
    fun friendsFlow_sendNormalize_errors_acceptReject_mutualAutoAccept_andStories() {
        val email = E2eTestHarness.uniqueEmail("friends_main")
        val peerOutgoing = E2eTestHarness.createPeer("peer_out", displayName = "OutgoingBuddy")
        val peerAccept = E2eTestHarness.createPeer("peer_acc", displayName = "AcceptBuddy")
        val peerReject = E2eTestHarness.createPeer("peer_rej", displayName = "RejectBuddy")
        val peerMutual = E2eTestHarness.createPeer("peer_mut", displayName = "MutualBuddy")
        peers.addAll(listOf(peerOutgoing, peerAccept, peerReject, peerMutual))

        composeRule.signInWithEmailUi(email, "CozyPass123!")
        composeRule.openOasisSubTab("Friends")
        composeRule.waitUntilNodeExists(hasText("YOUR CONNECT CODE"))

        // Read User A's real 8-character player tag from UI (unmerged tree because Text is inside clickable Surface)
        composeRule.waitUntil(15_000L) {
            composeRule.onNodeWithTag("friends_connect_code", useUnmergedTree = true).readDisplayedText().length == 8
        }
        val myTag = composeRule.onNodeWithTag("friends_connect_code", useUnmergedTree = true).readDisplayedText()
        assertEquals(8, myTag.length)

        // Edge case: Self-add rejected with clear backend message
        sendFriendRequestViaDialog(myTag)
        composeRule.waitUntilNodeExists(hasText("You can't add yourself!", substring = true))
        composeRule.waitUntilNodeDoesNotExist(hasText("You can't add yourself!", substring = true))

        // Edge case: Non-existent player tag rejected
        sendFriendRequestViaDialog("ZZZZ9999")
        composeRule.waitUntilNodeExists(hasText("User not found", substring = true))
        composeRule.waitUntilNodeDoesNotExist(hasText("User not found", substring = true))

        // Happy path: Send request using lowercase + '#' prefix normalization
        sendFriendRequestViaDialog("#${peerOutgoing.playerTag.lowercase()}")
        composeRule.waitUntilNodeExists(hasText("Friend request sent!"))
        composeRule.waitUntilNodeDoesNotExist(hasText("Friend request sent!"))

        // Edge case: Duplicate outgoing request rejected
        sendFriendRequestViaDialog(peerOutgoing.playerTag)
        composeRule.waitUntilNodeExists(hasText("Request already sent!", substring = true))
        composeRule.waitUntilNodeDoesNotExist(hasText("Request already sent!", substring = true))

        // Incoming Request Reject flow: Peer Reject sends request -> User A rejects
        peerReject.sendFriendRequest(myTag)
        composeRule.onNodeWithText("Journey").performClick()
        composeRule.onNodeWithText("Friends").performClick()
        composeRule.waitUntilNodeExists(hasText("Pending Requests (1)"))
        composeRule.waitUntilNodeExists(hasTestTag("pending_request_card_${peerReject.playerTag}"))
        composeRule.onNodeWithText("Reject").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Pending Requests (1)"))
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (0)"))

        // Incoming Request Accept flow: Peer Accept sends request -> User A accepts -> shows no-session fallback
        peerAccept.sendFriendRequest(myTag)
        composeRule.onNodeWithText("Journey").performClick()
        composeRule.onNodeWithText("Friends").performClick()
        composeRule.waitUntilNodeExists(hasText("Pending Requests (1)"))
        composeRule.waitUntilNodeExists(hasTestTag("pending_request_card_${peerAccept.playerTag}"))
        composeRule.onNodeWithText("Accept").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Pending Requests (1)"))
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (1)"))
        composeRule.waitUntilNodeExists(hasText("AcceptBuddy #${peerAccept.playerTag}"))
        composeRule.waitUntilNodeExists(hasText("Just joined! No sessions yet."))

        // Mutual auto-accept: Peer Mutual sends request to User A; User A sends request back by tag
        peerMutual.sendFriendRequest(myTag)
        sendFriendRequestViaDialog(peerMutual.playerTag)
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (2)"))
        composeRule.waitUntilNodeExists(hasText("MutualBuddy #${peerMutual.playerTag}"))

        // Peer Mutual completes a focus session -> story appears after refreshing Friends tab
        peerMutual.completeFocusSession(tasksCompleted = 2, lastTaskText = "Study algorithms")
        composeRule.onNodeWithText("Journey").performClick()
        composeRule.onNodeWithText("Friends").performClick()
        composeRule.waitUntilNodeExists(hasText("Study algorithms", substring = true))
    }

    @Test
    fun safetyReportAndBlock_fromFriendsAndFocusRoom_includingRealtimeReciprocalBlock() {
        val email = E2eTestHarness.uniqueEmail("safety_main")
        val friendPeer = E2eTestHarness.createPeer("peer_friend_blk", displayName = "FriendToBlock")
        val roomPeer = E2eTestHarness.createPeer("peer_room_blk", displayName = "RoomBlockerTarget")
        val reciprocalPeer = E2eTestHarness.createPeer("peer_recip_blk", displayName = "ReciprocalBlocker")
        peers.addAll(listOf(friendPeer, roomPeer, reciprocalPeer))

        composeRule.signInWithEmailUi(email, "CozyPass123!")
        composeRule.openOasisSubTab("Friends")
        composeRule.waitUntil(15_000L) {
            composeRule.onNodeWithTag("friends_connect_code", useUnmergedTree = true).readDisplayedText().length == 8
        }
        val myTag = composeRule.onNodeWithTag("friends_connect_code", useUnmergedTree = true).readDisplayedText()

        // Establish accepted friendship with friendPeer
        friendPeer.sendFriendRequest(myTag)
        composeRule.onNodeWithText("Journey").performClick()
        composeRule.onNodeWithText("Friends").performClick()
        composeRule.waitUntilNodeExists(hasText("FriendToBlock"))
        composeRule.onNodeWithText("Accept").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Pending Requests (1)"))
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (1)"))
        composeRule.waitUntilNodeExists(hasTestTag("friends_card_${friendPeer.userId}"))
        composeRule.waitForIdle()

        // Edge case: Open Report dialog and Cancel
        composeRule.onNodeWithTag("friends_more_${friendPeer.userId}").performClick()
        composeRule.waitUntilNodeExists(hasText("Report User"))
        composeRule.onNodeWithText("Report User").performClick()
        composeRule.waitUntilNodeExists(hasText("Inappropriate Name"))
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("Inappropriate Name"))

        // Happy path: Report friend -> confirmation snackbar
        composeRule.onNodeWithTag("friends_more_${friendPeer.userId}").performClick()
        composeRule.waitUntilNodeExists(hasText("Report User"))
        composeRule.onNodeWithText("Report User").performClick()
        composeRule.waitUntilNodeExists(hasText("Inappropriate Name"))
        composeRule.onNodeWithText("Inappropriate Name").performClick()
        composeRule.waitUntilNodeExists(hasText("Report submitted. Thank you."))

        // Happy path: Block friend from Friends -> immediately disappears from Neighbourhood
        composeRule.onNodeWithTag("friends_more_${friendPeer.userId}").performClick()
        composeRule.waitUntilNodeExists(hasText("Block User"))
        composeRule.onNodeWithText("Block User").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasTestTag("friends_card_${friendPeer.userId}"))
        composeRule.waitUntilNodeExists(hasText("Your Neighbourhood (0)"))

        // Navigate Home -> Enter Focus Room
        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitUntilNodeExists(hasText("Start Focus"))
        composeRule.onNodeWithText("Start Focus").performClick()
        composeRule.waitUntilNodeExists(hasText("Cozy Room"))

        // Blocked friendPeer joins room -> stays completely hidden from User A
        friendPeer.joinRoom(activeTaskText = "Should stay hidden")
        // Non-blocked roomPeer joins room -> User A sees only roomPeer (2 focusing now, not 3)
        roomPeer.joinRoom(activeTaskText = "Visible room peer")
        composeRule.waitUntilNodeExists(hasText("RoomBlockerTarget"))
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))
        composeRule.onNodeWithText("FriendToBlock").assertDoesNotExist()

        // Block roomPeer directly from Focus Room card -> disappears immediately
        composeRule.onNodeWithTag("room_participant_options_${roomPeer.userId}").performClick()
        composeRule.waitUntilNodeExists(hasText("Block User"))
        composeRule.onNodeWithText("Block User").performClick()
        composeRule.waitUntilNodeDoesNotExist(hasText("RoomBlockerTarget"))
        composeRule.waitUntilNodeExists(hasText("1 focusing now"))

        // Edge case: Real-time reciprocal block via /ws/safety when peer blocks User A
        reciprocalPeer.joinRoom(activeTaskText = "About to block you")
        composeRule.waitUntilNodeExists(hasText("ReciprocalBlocker"))
        composeRule.waitUntilNodeExists(hasText("2 focusing now"))

        // Look up User A's profile ID via ServiceLocator in test (or via friend/room) -> reciprocalPeer blocks User A
        val myUserId = com.grepho.cozydoubling.core.di.ServiceLocator.authRepository.getCurrentUserId()!!
        reciprocalPeer.blockUser(myUserId)
        composeRule.waitUntilNodeDoesNotExist(hasText("ReciprocalBlocker"))
        composeRule.waitUntilNodeExists(hasText("1 focusing now"))
    }
}
