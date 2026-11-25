package com.grepho.cozydoubling

import androidx.compose.ui.graphics.Color
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.data.fake.FakeAppContainer
import com.grepho.cozydoubling.data.local.InMemoryTokenStore
import com.grepho.cozydoubling.data.remote.*
import com.grepho.cozydoubling.features.room.FocusSession
import com.grepho.cozydoubling.features.room.ParticipantPresence
import com.grepho.cozydoubling.features.shop.ShopItemUiState
import kotlinx.coroutines.flow.*
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

        // 3. FocusSessionDto -> FocusSession mapping
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
        val sessionObj = apiClient.json.decodeFromString<FocusSessionDto>(sessionJson).toDomain()
        assertEquals("sess_abc", sessionObj.id)
        assertEquals(34L, sessionObj.earnedLeaves)
        assertEquals(25, sessionObj.durationMinutes)

        // 3b. ProfileDto -> Profile mapping
        val profileJson = """
            {
                "id": "prof_1",
                "display_name": "Osman",
                "player_tag": "OSMN1234",
                "leaves": 42,
                "total_minutes_focused": 120,
                "is_supporter": true
            }
        """.trimIndent()
        val profileObj = apiClient.json.decodeFromString<ProfileDto>(profileJson).toDomain()
        assertEquals("prof_1", profileObj.id)
        assertEquals("Osman", profileObj.displayName)
        assertEquals(42L, profileObj.leaves)
        assertTrue(profileObj.isSupporter)

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
        val wsUpdate = WsPresenceUpdate(presence = ParticipantPresenceDto.fromDomain(presence))
        val wsJson = apiClient.json.encodeToString(wsUpdate)
        assertTrue(wsJson.contains("presence_update"))
        assertTrue(wsJson.contains("Cozy Tester"))

        val wsRoomMsg = apiClient.json.decodeFromString<WsRoomMessage>(
            """{"type":"presence_state","participants":[{"id":"u2","name":"Buddy","active_task":"Reading","completed_tasks":1,"total_tasks":2}]}"""
        )
        assertEquals("presence_state", wsRoomMsg.type)
        assertEquals(1, wsRoomMsg.participants?.size)
        assertEquals("Buddy", wsRoomMsg.participants?.first()?.name)

        // Verify null active_task tolerance in ParticipantPresenceDto -> ParticipantPresence
        val nullTaskJson = """{"id":"u3","name":"NullTasker","active_task":null,"completed_tasks":0,"total_tasks":0}"""
        val nullTaskPresence = apiClient.json.decodeFromString<ParticipantPresenceDto>(nullTaskJson).toDomain()
        assertEquals("Focusing", nullTaskPresence.displayTaskText)
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
        val mockBlockedIds = mutableSetOf("blocked_bad_user")
        val mockFriendIds = mutableSetOf("friend_99")

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
                url.endsWith("/api/safety/blocks") -> {
                    val idsJson = mockBlockedIds.joinToString(",") { "\"$it\"" }
                    """{"blocked_user_ids":[$idsJson]}"""
                }
                url.endsWith("/api/safety/blocked-profiles") -> {
                    val profilesJson = mockBlockedIds.joinToString(",") { id ->
                        """{"id":"$id","display_name":"Bad User","player_tag":"BADD9999"}"""
                    }
                    "[$profilesJson]"
                }
                url.endsWith("/api/safety/block") -> {
                    val buf = okio.Buffer()
                    request.body?.writeTo(buf)
                    val reqDto = ApiClient().json.decodeFromString<BlockUserRequestDto>(buf.readUtf8())
                    mockBlockedIds.add(reqDto.targetId)
                    """{"ok":true}"""
                }
                url.endsWith("/api/safety/unblock") -> {
                    val buf = okio.Buffer()
                    request.body?.writeTo(buf)
                    val reqDto = ApiClient().json.decodeFromString<BlockUserRequestDto>(buf.readUtf8())
                    mockBlockedIds.remove(reqDto.targetId)
                    """{"ok":true}"""
                }
                method == "DELETE" && url.startsWith("/api/friends/") -> {
                    val fid = url.removePrefix("/api/friends/")
                    mockFriendIds.remove(fid)
                    """{"ok":true}"""
                }
                url.endsWith("/api/friends") -> {
                    if (mockFriendIds.contains("friend_99")) {
                        """
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
                    } else {
                        "[]"
                    }
                }
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

        // Test Friends Repo (including removeFriend SOC-05)
        val friendsRepo = RemoteFriendsRepository(apiClient, profileRepo)
        val friends = friendsRepo.fetchFriendsWithStories()
        assertEquals(1, friends.size)
        assertEquals("Buddy", friends[0].name)
        friendsRepo.removeFriend("friend_99")
        assertTrue(friendsRepo.friends.value.isEmpty())
        val reqs = friendsRepo.fetchIncomingRequests()
        assertEquals(1, reqs.size)
        assertEquals("Requester", reqs[0].displayName)

        // Test Safety Repo (including blockedProfiles & unblockUser SAFE-04)
        val safetyRepo = RemoteSafetyRepository(apiClient, authRepo)
        safetyRepo.blockUser("bad_guy_123")
        assertTrue(safetyRepo.blockedUserIds.value.contains("bad_guy_123"))
        safetyRepo.refreshBlockedUsers()
        assertTrue(safetyRepo.blockedUsers.value.any { it.id == "bad_guy_123" })
        safetyRepo.unblockUser("bad_guy_123")
        assertFalse(safetyRepo.blockedUserIds.value.contains("bad_guy_123"))
        assertFalse(safetyRepo.blockedUsers.value.any { it.id == "bad_guy_123" })
    }

    @Test
    fun testRemoteAuthRepository_persistentTokenRestoreAndFailureModes() = runBlocking {
        // 1. Valid stored token restores Authenticated state on init
        val validClient = ApiClient(
            baseUrl = "http://mockserver:8000",
            customOkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
                val req = chain.request()
                val authHeader = req.header("Authorization")
                assertEquals("Bearer persisted_jwt_token", authHeader)
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"id":"u_restored","display_name":"Restored","player_tag":"REST1234"}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }.build()
        )
        val validStore = InMemoryTokenStore("persisted_jwt_token")
        val validRepo = RemoteAuthRepository(validClient, validStore)
        validRepo.restoreSession()
        assertEquals(AuthStatus.Authenticated("u_restored"), validRepo.authStatus.value)
        assertEquals("persisted_jwt_token", validClient.authToken)

        // 2. 401 Unauthorized clears TokenStore and transitions to NotAuthenticated
        val expiredClient = ApiClient(
            baseUrl = "http://mockserver:8000",
            customOkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("""{"detail":"Token expired"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()
        )
        val expiredStore = InMemoryTokenStore("expired_jwt_token")
        val expiredRepo = RemoteAuthRepository(expiredClient, expiredStore)
        expiredRepo.restoreSession()
        assertEquals(AuthStatus.NotAuthenticated, expiredRepo.authStatus.value)
        assertNull(expiredStore.loadToken())
        assertNull(expiredClient.authToken)

        // 3. Transient network error keeps token persisted & notifies transient failure
        var transientCallbackInvoked = false
        val offlineClient = ApiClient(
            baseUrl = "http://mockserver:8000",
            customOkHttpClient = OkHttpClient.Builder().addInterceptor {
                throw java.io.IOException("Network unreachable")
            }.build()
        )
        val offlineStore = InMemoryTokenStore("offline_valid_token")
        val offlineRepo = RemoteAuthRepository(
            apiClient = offlineClient,
            tokenStore = offlineStore,
            onTransientRestoreFailure = { transientCallbackInvoked = true }
        )
        offlineRepo.restoreSession()
        assertEquals(AuthStatus.Initializing, offlineRepo.authStatus.value)
        assertEquals("offline_valid_token", offlineStore.loadToken())
        assertTrue(transientCallbackInvoked)

        // 4. Sign out while offline still clears TokenStore and logs out locally
        offlineRepo.signOut()
        assertEquals(AuthStatus.NotAuthenticated, offlineRepo.authStatus.value)
        assertNull(offlineStore.loadToken())
        assertNull(offlineClient.authToken)
    }

    @Test
    fun testFocusRoomMessageParsingAndFlowCombination() = runBlocking {
        val apiClient = ApiClient(baseUrl = "http://localhost:8000")

        // 1. Backend presence_state JSON
        val stateJson = """{"type":"presence_state","participants":[{"id":"1a2fffb5-9eb3-4d21-9e87-7706381f7574:1cd64636-9c2d-4a4f-af69-6eadb6cd77e5","name":"Cozy Doubler","active_task":"Focusing","completed_tasks":0,"total_tasks":0}]}"""
        val stateMsg = apiClient.json.decodeFromString<WsRoomMessage>(stateJson)
        assertEquals("presence_state", stateMsg.type)
        assertEquals(1, stateMsg.participants?.size)
        assertEquals("Cozy Doubler", stateMsg.participants?.first()?.name)

        // 2. Backend presence_update JSON
        val updateJson = """{"type":"presence_update","presence":{"id":"2952e34a-d43b-4d6e-bf39-b6ef9f38cd00:0dfe19de-61af-47dd-872a-171112ea69ad","name":"emin","active_task":"Focusing","completed_tasks":0,"total_tasks":0}}"""
        val updateMsg = apiClient.json.decodeFromString<WsRoomMessage>(updateJson)
        assertEquals("presence_update", updateMsg.type)
        assertEquals("emin", updateMsg.presence?.name)

        // 3. Test the flow combination logic from FocusRoomViewModel
        val presenceFlow = kotlinx.coroutines.flow.MutableStateFlow<List<ParticipantPresence>>(emptyList())
        val broadcastFlow = kotlinx.coroutines.flow.MutableSharedFlow<ParticipantPresence>(replay = 0, extraBufferCapacity = 64)
        val blockedIdsFlow = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())

        val latestBroadcasts = broadcastFlow
            .scan(emptyMap<String, ParticipantPresence>()) { acc, update ->
                acc + (update.id to update)
            }
            .onStart { emit(emptyMap()) }

        val myId = "1a2fffb5-9eb3-4d21-9e87-7706381f7574:1cd64636-9c2d-4a4f-af69-6eadb6cd77e5"

        val otherParticipantsFlow = presenceFlow
            .map { list -> list.associateBy { it.id } }
            .combine(latestBroadcasts) { presenceMap, broadcasts ->
                (presenceMap + broadcasts)
            }
            .combine(blockedIdsFlow) { participantsMap, blockedIds ->
                val myProfileId = myId.split(":").first()
                participantsMap.values.filter { participant ->
                    val profileId = participant.id.split(":").first()
                    profileId != myProfileId && profileId !in blockedIds
                }
            }

        val p2 = updateMsg.presence!!.toDomain()

        // Test case: Flow combination receives presence and broadcast
        val testPresenceFlow = MutableStateFlow<List<ParticipantPresence>>(emptyList())
        val testBroadcastFlow = MutableSharedFlow<ParticipantPresence>(replay = 1, extraBufferCapacity = 64)
        val testLatestBroadcasts = testBroadcastFlow
            .scan(emptyMap<String, ParticipantPresence>()) { acc, update ->
                acc + (update.id to update)
            }
            .onStart { emit(emptyMap()) }

        val testFlow = testPresenceFlow
            .map { list -> list.associateBy { it.id } }
            .combine(testLatestBroadcasts) { presenceMap, broadcasts ->
                (presenceMap + broadcasts)
            }

        testBroadcastFlow.tryEmit(p2)
        testPresenceFlow.value = listOf(p2)
        
        val result = testFlow.first { it.isNotEmpty() }
        assertEquals(1, result.size)
        assertEquals("emin", result[p2.id]?.name)

        // Test receiving an update on Device 1 when Device 2 joins
        presenceFlow.value = listOf(p2)
        broadcastFlow.tryEmit(p2)

        val firstEmission = otherParticipantsFlow.first()
        assertEquals(1, firstEmission.size)
        assertEquals("emin", firstEmission.first().name)

        // Verify deduplication when multiple presence entries exist for the same profileId
        val p2Duplicate = p2.copy(id = "${p2.id.split(":").first()}:new_session", activeTaskText = "New Task")
        val presenceWithDuplicates = listOf(p2, p2Duplicate)
        val deduplicated = presenceWithDuplicates
            .groupBy { it.id.split(":").first() }
            .map { (profileId, presenceList) ->
                val p = presenceList.last()
                com.grepho.cozydoubling.features.room.RoomParticipant(
                    id = profileId,
                    name = p.name,
                    activeTaskText = p.displayTaskText,
                    completedTasks = p.completedTasks,
                    totalTasks = p.totalTasks,
                )
            }
        assertEquals(1, deduplicated.size)
        assertEquals("New Task", deduplicated.first().activeTaskText)
    }
}
