package com.grepho.cozydoubling

import com.grepho.cozydoubling.data.fake.FakeProfileRepository

import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.core.room.FocusNotificationAction
import com.grepho.cozydoubling.data.fake.FakeAppContainer
import com.grepho.cozydoubling.features.shop.ShopItemUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRepositoriesTest {

    private val container = FakeAppContainer()

    @Test
    fun testAuthRepository_signInAndSignOut() = runBlocking {
        val authRepo = container.authRepository
        assertTrue(authRepo.authStatus.value is AuthStatus.Authenticated)
        assertEquals("user_cozy_001", authRepo.getCurrentUserId())

        authRepo.signOut()
        assertTrue(authRepo.authStatus.value is AuthStatus.NotAuthenticated)

        authRepo.signInWithEmail("test@example.com", "pass123")
        assertTrue(authRepo.authStatus.value is AuthStatus.Authenticated)
    }

    @Test
    fun testProfileRepository_updateNameAndLeaves() = runBlocking {
        val profileRepo = container.profileRepository
        assertEquals("Cozy Doubler", profileRepo.profile.value?.displayName)
        assertEquals(150L, profileRepo.profile.value?.leaves)

        profileRepo.updateDisplayName("Focus Hero")
        assertEquals("Focus Hero", profileRepo.profile.value?.displayName)

        (profileRepo as FakeProfileRepository).updateLeaves(25)
        assertEquals(175L, profileRepo.profile.value?.leaves)
    }

    @Test
    fun testEconomyRepository_purchaseThemeAndEquip() = runBlocking {
        val profileRepo = container.profileRepository
        val economyRepo = container.economyRepository

        // Initial leaves = 150
        val peachTheme = economyRepo.shopItems.value.filterIsInstance<ShopItemUiState.Theme>()
            .find { it.id == "theme_peach_blossom" }
        assertNotNull(peachTheme)
        assertFalse(peachTheme!!.isOwned)

        // Buy with leaves (costs 100)
        economyRepo.purchaseWithLeaves("theme_peach_blossom")
        assertEquals(50L, profileRepo.profile.value?.leaves)

        val updatedPeach = economyRepo.shopItems.value.filterIsInstance<ShopItemUiState.Theme>()
            .find { it.id == "theme_peach_blossom" }
        assertTrue(updatedPeach!!.isOwned)

        // Equip
        economyRepo.equipTheme("theme_peach_blossom")
        assertEquals("theme_peach_blossom", profileRepo.profile.value?.equippedThemeId)

        // Unequip
        economyRepo.unequipTheme()
        assertEquals(null, profileRepo.profile.value?.equippedThemeId)
    }

    @Test
    fun testFocusRoomRepository_sessionLifecycleAndLeavesCalculation() = runBlocking {
        val profileRepo = container.profileRepository
        val roomRepo = container.focusRoomRepository

        val initialLeaves = profileRepo.profile.value?.leaves ?: 0L
        val sessionId = roomRepo.startSession()
        assertNotNull(sessionId)

        // Finish session with 3 completed tasks
        roomRepo.finishSession(sessionId!!, tasksCompleted = 3, taskText = "Finished Chapter 1")

        val finishedSession = roomRepo.fetchSession(sessionId)
        assertNotNull(finishedSession)
        assertTrue(finishedSession!!.isProcessed)
        assertEquals(3, finishedSession.tasksCompleted)

        // Formula: duration (min 25) * 1 + tasks (3) * 3 = 25 + 9 = 34 leaves
        assertEquals(34L, finishedSession.earnedLeaves)
        assertEquals(initialLeaves + 34L, profileRepo.profile.value?.leaves)
    }

    @Test
    fun testFriendsRepository_sendAndAccept() = runBlocking {
        val friendsRepo = container.friendsRepository

        // Accepts pending request
        assertEquals(1, friendsRepo.pendingRequests.value.size)
        val pendingId = friendsRepo.pendingRequests.value.first().id
        friendsRepo.acceptFriendRequest(pendingId)

        assertEquals(0, friendsRepo.pendingRequests.value.size)
        assertTrue(friendsRepo.friends.value.any { it.id == pendingId })

        // Remove friend (SOC-05)
        friendsRepo.removeFriend(pendingId)
        assertFalse(friendsRepo.friends.value.any { it.id == pendingId })
    }

    @Test
    fun testFriendsRepository_blockedUserCannotBeAddedAndErrorsClearly() = runBlocking {
        val friendsRepo = container.friendsRepository
        val safetyRepo = container.safetyRepository

        safetyRepo.blockUser("BADU8888")
        var thrownMessage: String? = null
        try {
            friendsRepo.sendFriendRequest("BADU8888")
        } catch (e: Exception) {
            thrownMessage = e.message
        }
        assertNotNull(thrownMessage)
        assertTrue(thrownMessage!!.contains("blocked", ignoreCase = true))

        // Unblocking allows adding again
        safetyRepo.unblockUser("BADU8888")
        val msg = friendsRepo.sendFriendRequest("BADU8888")
        assertEquals("Friend request sent!", msg)
    }

    @Test
    fun testSafetyRepository_blockUser() = runBlocking {
        val safetyRepo = container.safetyRepository
        assertTrue(safetyRepo.blockedUserIds.value.isEmpty())

        safetyRepo.blockUser("annoying_user_1")
        assertTrue(safetyRepo.blockedUserIds.value.contains("annoying_user_1"))
        assertTrue(safetyRepo.blockedUsers.value.any { it.id == "annoying_user_1" })

        // Unblock user (SAFE-04)
        safetyRepo.unblockUser("annoying_user_1")
        assertFalse(safetyRepo.blockedUserIds.value.contains("annoying_user_1"))
        assertFalse(safetyRepo.blockedUsers.value.any { it.id == "annoying_user_1" })
    }

    @Test
    fun testFocusSessionController_lifecycleAndActions() = runBlocking {
        val controller = container.focusSessionController
        assertEquals(null, controller.loadPersistedSession())
        assertTrue(controller.isPipEnabled.value)

        val snapshot = com.grepho.cozydoubling.core.room.ActiveFocusSessionSnapshot(
            sessionId = "sess_100",
            startTimeEpochMs = 1_700_000_000_000L,
            activeTaskText = "Study biology",
            completedTasks = 1,
            totalTasks = 3,
            participantCount = 2
        )
        controller.startOrResumeSession(snapshot)
        assertEquals("sess_100" to 1_700_000_000_000L, controller.loadPersistedSession())
        assertEquals("Study biology", controller.activeSession.value?.activeTaskText)

        controller.setPipEnabled(false)
        assertFalse(controller.isPipEnabled.value)

        controller.stopSession()
        assertNull(controller.loadPersistedSession())
        assertNull(controller.activeSession.value)

        // Updating after stopSession or with a mismatched session ID must never re-persist
        controller.updateSession(snapshot)
        assertNull(controller.loadPersistedSession())
        assertNull(controller.activeSession.value)

        val secondSnapshot = snapshot.copy(sessionId = "sess_200", activeTaskText = "New session")
        controller.startOrResumeSession(secondSnapshot)
        controller.updateSession(snapshot) // stale ID sess_100 ignored
        assertEquals("sess_200", controller.activeSession.value?.sessionId)

        val receivedActions = mutableListOf<FocusNotificationAction>()
        val collectJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            controller.notificationActions.collect { receivedActions.add(it) }
        }
        controller.emitNotificationAction(FocusNotificationAction.FinishSession("sess_100")) // mismatched -> ignored
        controller.emitNotificationAction(FocusNotificationAction.FinishSession("sess_200")) // matched -> delivered
        kotlinx.coroutines.yield()
        assertEquals(listOf(FocusNotificationAction.FinishSession("sess_200")), receivedActions)
        collectJob.cancel()
        controller.stopSession()
    }

    @Test
    fun testBillingRepository_purchaseSupporter() = runBlocking {
        val profileRepo = container.profileRepository
        val billingRepo = container.billingRepository

        assertFalse(profileRepo.profile.value?.isSupporter ?: true)

        billingRepo.restorePurchases()
        assertTrue(profileRepo.profile.value?.isSupporter ?: false)
    }

    @Test
    fun testProfileRepository_deleteAccountAndReLogin() = runBlocking {
        val profileRepo = container.profileRepository
        val authRepo = container.authRepository

        // 1. Initial user has 150 leaves
        assertEquals("user_cozy_001", profileRepo.profile.value?.id)
        (profileRepo as FakeProfileRepository).updateLeaves(50)
        assertEquals(200L, profileRepo.profile.value?.leaves)

        // 2. Delete account
        assertTrue(profileRepo.deleteAccount().isSuccess)
        assertEquals(null, profileRepo.profile.value)
        assertTrue(authRepo.authStatus.value is AuthStatus.NotAuthenticated)

        // 3. Re-login with Google -> fresh default profile created
        authRepo.signInWithGoogle(android.app.Activity())
        profileRepo.refreshProfile()
        assertNotNull(profileRepo.profile.value)
        assertEquals("user_cozy_001", profileRepo.profile.value?.id)
        assertEquals("Cozy Doubler", profileRepo.profile.value?.displayName)
        // Reset to initial 150 leaves, not 200
        assertEquals(150L, profileRepo.profile.value?.leaves)
    }

    @Test
    fun testProfileRepository_reviewerAccessLogin() = runBlocking {
        val profileRepo = container.profileRepository
        val authRepo = container.authRepository

        // Sign in with reviewer email
        authRepo.signInWithEmail("alice@cozydoubling.com", "pass")
        profileRepo.refreshProfile()

        val aliceProfile = profileRepo.profile.value
        assertNotNull(aliceProfile)
        assertEquals("user_alice", aliceProfile?.id)
        assertEquals("Alice", aliceProfile?.displayName)
        assertEquals(150L, aliceProfile?.leaves)
    }
}
