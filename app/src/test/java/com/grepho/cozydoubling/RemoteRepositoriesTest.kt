package com.grepho.cozydoubling

import androidx.compose.ui.graphics.Color
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.data.fake.FakeAppContainer
import com.grepho.cozydoubling.data.remote.*
import com.grepho.cozydoubling.features.room.FocusSession
import com.grepho.cozydoubling.features.room.ParticipantPresence
import com.grepho.cozydoubling.features.shop.ShopItemUiState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class RemoteRepositoriesTest {

    @Test
    fun testParseHexColor() {
        val color6 = parseHexColor("#FF0000")
        assertEquals(Color(0xFFFF0000), color6)

        val color8 = parseHexColor("#80FF0000")
        assertEquals(Color(0x80FF0000), color8)

        val colorBare = parseHexColor("00FF00")
        assertEquals(Color(0xFF00FF00), colorBare)
    }

    @Test
    fun testDtoSerializationAndDeserialization() {
        val apiClient = ApiClient(baseUrl = "http://localhost:8000")

        // 1. Auth DTO
        val authJson = """{"access_token":"jwt_test_123","token_type":"bearer"}"""
        val authDto = apiClient.json.decodeFromString<AuthResponseDto>(authJson)
        assertEquals("jwt_test_123", authDto.accessToken)

        // 2. Session DTOs
        val startSessionJson = """{"session_id":"sess_abc"}"""
        val startDto = apiClient.json.decodeFromString<StartSessionResponseDto>(startSessionJson)
        assertEquals("sess_abc", startDto.sessionId)

        val finishReq = FinishSessionRequestDto(tasksCompleted = 4, lastTaskText = "Reviewing code")
        val finishReqJson = apiClient.json.encodeToString(finishReq)
        assertTrue(finishReqJson.contains("tasks_completed"))
        assertTrue(finishReqJson.contains("4"))

        // 3. FocusSession DTO
        val sessionJson = """
            {
                "id": "sess_abc",
                "user_id": "u1",
                "start_time": "2026-11-20T10:00:00",
                "end_time": "2026-11-20T10:25:00",
                "tasks_completed": 3,
                "earned_leaves": 34,
                "is_processed": true,
                "duration_minutes": 25
            }
        """.trimIndent()
        val sessionObj = apiClient.json.decodeFromString<FocusSession>(sessionJson)
        assertEquals("sess_abc", sessionObj.id)
        assertEquals(34L, sessionObj.earnedLeaves)
        assertEquals(25, sessionObj.durationMinutes)

        // 4. Friend DTOs
        val storyJson = """
            [
                {
                    "friend_id": "f_1",
                    "display_name": "Oliver",
                    "player_tag": "OLIV9999",
                    "duration_minutes": 45,
                    "end_time": "2026-11-20T12:00:00",
                    "last_task_text": "Math study",
                    "accepted_at": "2026-11-01T00:00:00"
                }
            ]
        """.trimIndent()
        val stories = apiClient.json.decodeFromString<List<FriendStoryDto>>(storyJson)
        assertEquals(1, stories.size)
        assertEquals("Oliver", stories[0].displayName)

        // 5. WebSocket Presence DTOs
        val presence = ParticipantPresence(
            id = "u1:sess1",
            name = "Cozy Tester",
            activeTaskText = "Building remote client",
            completedTasks = 2,
            totalTasks = 3
        )
        val wsUpdate = WsPresenceUpdate(presence = presence)
        val wsJson = apiClient.json.encodeToString(wsUpdate)
        assertTrue(wsJson.contains("presence_update"))
        assertTrue(wsJson.contains("Cozy Tester"))

        val wsRoomMsg = apiClient.json.decodeFromString<WsRoomMessage>(
            """{"type":"presence_state","participants":[{"id":"u2","name":"Buddy","active_task":"Reading","completed_tasks":1,"total_tasks":2}]}"""
        )
        assertEquals("presence_state", wsRoomMsg.type)
        assertEquals(1, wsRoomMsg.participants?.size)
        assertEquals("Buddy", wsRoomMsg.participants?.first()?.name)
    }

    @Test
    fun testDefaultAppContainerAndServiceLocator() {
        val container = DefaultAppContainer(baseUrl = "http://127.0.0.1:8000")
        assertNotNull(container.authRepository)
        assertNotNull(container.profileRepository)
        assertNotNull(container.focusRoomRepository)
        assertNotNull(container.economyRepository)
        assertNotNull(container.friendsRepository)
        assertNotNull(container.safetyRepository)
        assertNotNull(container.notificationRepository)
        assertNotNull(container.billingRepository)

        // Verify ServiceLocator switching
        ServiceLocator.useFakeContainer()
        assertTrue(ServiceLocator.container is FakeAppContainer)

        ServiceLocator.useDefaultContainer("http://10.0.2.2:8000")
        assertTrue(ServiceLocator.container is DefaultAppContainer)
    }

    @Test
    fun testMockedRemoteRepositoriesWorkflow() = runBlocking {
        // Intercept HTTP calls to verify client-backend interaction end-to-end
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val url = request.url.encodedPath
            val method = request.method

            val responseBody = when {
                url.endsWith("/api/auth/login") -> """{"access_token":"mock_jwt_token","token_type":"bearer"}"""
                url.endsWith("/api/auth/me") || url.endsWith("/api/profile") -> """
                    {
                        "id": "user_mock_001",
                        "display_name": "Mock Cozy",
                        "player_tag": "MOCK1234",
                        "leaves": 250,
                        "total_minutes_focused": 100,
                        "is_supporter": false,
                        "equipped_theme_id": "system_default"
                    }
                """.trimIndent()
                url.endsWith("/api/shop/items") -> """
                    [
                        {
                            "id": "system_default",
                            "name": "System Default",
                            "category_id": "theme",
                            "is_premium": false,
                            "leaf_price": 0,
                            "is_owned": true,
                            "is_equipped": true
                        },
                        {
                            "id": "theme_peach_blossom",
                            "name": "Peach Blossom",
                            "category_id": "theme",
                            "is_premium": false,
                            "leaf_price": 100,
                            "is_owned": false,
                            "is_equipped": false,
                            "palette": {
                                "primary": "#C0583E",
                                "on_primary": "#FFFFFF",
                                "primary_container": "#FFDBD1",
                                "on_primary_container": "#3B0900",
                                "secondary": "#77574E",
                                "on_secondary": "#FFFFFF",
                                "secondary_container": "#FFDBD1",
                                "on_secondary_container": "#2C150F",
                                "tertiary": "#6C5D2F",
                                "on_tertiary": "#FFFFFF",
                                "tertiary_container": "#F6E1A6",
                                "on_tertiary_container": "#231B00",
                                "surface": "#FFF8F6",
                                "on_surface": "#231917",
                                "background": "#FFF8F6",
                                "on_background": "#231917",
                                "surface_variant": "#F5DED8",
                                "on_surface_variant": "#53433F"
                            }
                        }
                    ]
                """.trimIndent()
                url.endsWith("/api/sessions/start") -> """{"session_id":"mock_sess_999"}"""
                url.endsWith("/api/sessions/mock_sess_999/finish") -> """
                    {
                        "id": "mock_sess_999",
                        "user_id": "user_mock_001",
                        "start_time": "2026-11-20T10:00:00",
                        "end_time": "2026-11-20T10:25:00",
                        "tasks_completed": 2,
                        "earned_leaves": 31,
                        "is_processed": true,
                        "duration_minutes": 25
                    }
                """.trimIndent()
                url.endsWith("/api/safety/blocks") -> """{"blocked_user_ids":["blocked_bad_user"]}"""
                url.endsWith("/api/safety/block") -> """{"ok":true}"""
                url.endsWith("/api/friends") -> """
                    [
                        {
                            "friend_id": "friend_99",
                            "display_name": "Buddy",
                            "player_tag": "BUDD9999",
                            "duration_minutes": 30,
                            "end_time": "2026-11-20T11:00:00",
                            "last_task_text": "Writing docs",
                            "accepted_at": "2026-11-01T00:00:00"
                        }
                    ]
                """.trimIndent()
                url.endsWith("/api/friends/requests") -> """
                    [
                        {
                            "id": "req_user_1",
                            "display_name": "Requester",
                            "player_tag": "REQU1111"
                        }
                    ]
                """.trimIndent()
                else -> """{"ok":true}"""
            }

            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }

        val baseOkHttpClient = OkHttpClient.Builder().build()
        val customClient = baseOkHttpClient.newBuilder().addInterceptor(interceptor).build()
        val apiClient = ApiClient(baseUrl = "http://mockserver:8000", customOkHttpClient = customClient)
        val authRepo = RemoteAuthRepository(apiClient)

        // Test Sign In
        val loginRes = authRepo.signInWithEmail("tester@cozydoubling.com", "pass123")
        assertTrue(loginRes.isSuccess)
        assertEquals("mock_jwt_token", apiClient.authToken)
        assertEquals("user_mock_001", authRepo.getCurrentUserId())
        assertTrue(authRepo.authStatus.value is AuthStatus.Authenticated)

        // Test Profile Repo
        val profileRepo = RemoteProfileRepository(apiClient, authRepo)
        profileRepo.refreshProfile()
        assertEquals("Mock Cozy", profileRepo.profile.value?.displayName)
        assertEquals(250L, profileRepo.profile.value?.leaves)

        // Test Focus Room Repo
        val roomRepo = RemoteFocusRoomRepository(apiClient, profileRepo)
        val sessId = roomRepo.startSession()
        assertEquals("mock_sess_999", sessId)
        roomRepo.finishSession(sessId!!, 2, "Finished task")

        // Test Economy Repo
        val economyRepo = RemoteEconomyRepository(apiClient, profileRepo)
        val items = economyRepo.fetchShopItems()
        assertEquals(2, items.size)
        val peach = items.filterIsInstance<ShopItemUiState.Theme>().find { it.id == "theme_peach_blossom" }
        assertNotNull(peach)
        assertEquals(100, peach?.leafPrice)

        // Test Friends Repo
        val friendsRepo = RemoteFriendsRepository(apiClient, profileRepo)
        val friends = friendsRepo.fetchFriendsWithStories()
        assertEquals(1, friends.size)
        assertEquals("Buddy", friends[0].name)
        val reqs = friendsRepo.fetchIncomingRequests()
        assertEquals(1, reqs.size)
        assertEquals("Requester", reqs[0].displayName)

        // Test Safety Repo
        val safetyRepo = RemoteSafetyRepository(apiClient, authRepo)
        safetyRepo.blockUser("bad_guy_123")
        assertTrue(safetyRepo.blockedUserIds.value.contains("bad_guy_123"))
    }
}
