package com.grepho.cozydoubling.e2e

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.grepho.cozydoubling.BuildConfig
import com.grepho.cozydoubling.core.MainActivity
import com.grepho.cozydoubling.core.di.ServiceLocator
import com.grepho.cozydoubling.features.room.LocalTaskDataSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Shared E2E test harness and live backend peer client.
 *
 * Lives strictly in androidTest so production Android UI/domain code remains
 * completely decoupled from backend networking and test scaffolding.
 */
object E2eTestHarness {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val sharedHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
    }

    fun grantRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val pkg = instrumentation.targetContext.packageName
            instrumentation.uiAutomation.grantRuntimePermission(
                pkg,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    /**
     * Resets process-wide repository state and local persisted tasks before launching MainActivity.
     */
    fun resetAppState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        LocalTaskDataSource.init(context)
        LocalTaskDataSource.clear()
        ServiceLocator.useDefaultContainer()
    }

    fun launchMainActivity(): ActivityScenario<MainActivity> {
        grantRuntimePermissions()
        resetAppState()
        return ActivityScenario.launch(MainActivity::class.java)
    }

    fun uniqueEmail(prefix: String): String {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
        return "e2e_${prefix}_$suffix@cozytest.local"
    }

    fun createPeer(
        prefix: String,
        displayName: String? = null,
        password: String = "CozyPass123!"
    ): BackendPeerClient {
        val email = uniqueEmail(prefix)
        val peer = BackendPeerClient(
            baseUrl = BuildConfig.API_BASE_URL,
            httpClient = sharedHttpClient,
            json = json
        )
        peer.registerAndFetchProfile(email, password, displayName)
        return peer
    }

    /**
     * Uses the real authenticated backend session endpoints (/api/sessions/start + /finish)
     * as the given user account so Shop E2E tests can earn real leaves without any backend backdoor.
     */
    fun completeRealSessionViaApi(
        email: String,
        password: String,
        tasksCompleted: Int,
        lastTaskText: String = "Deep Focus Session"
    ) {
        val peer = BackendPeerClient(
            baseUrl = BuildConfig.API_BASE_URL,
            httpClient = sharedHttpClient,
            json = json
        )
        peer.loginAndFetchProfile(email, password)
        peer.completeFocusSession(tasksCompleted = tasksCompleted, lastTaskText = lastTaskText)
        peer.close()
    }
}

/**
 * Live Peer (User B / User C) client talking directly to the same real FastAPI backend
 * via HTTP and WebSockets so multi-user room, friend, and safety flows are tested end-to-end.
 */
class BackendPeerClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient,
    private val json: Json
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    lateinit var token: String
        private set
    lateinit var userId: String
        private set
    lateinit var playerTag: String
        private set
    lateinit var displayName: String
        private set

    private var activeRoomSocket: WebSocket? = null
    private var activePresenceKey: String? = null

    fun registerAndFetchProfile(email: String, password: String, customDisplayName: String? = null) {
        val regBody = buildJsonObject {
            put("email", email.trim().lowercase())
            put("password", password)
        }.toString()
        val resp = post("/api/auth/register", regBody, authenticated = false)
        token = resp["access_token"]!!.jsonPrimitive.content

        if (!customDisplayName.isNullOrBlank()) {
            val patchBody = buildJsonObject {
                put("display_name", customDisplayName)
            }.toString()
            patch("/api/profile", patchBody)
        }
        refreshProfile()
    }

    fun loginAndFetchProfile(email: String, password: String) {
        val loginBody = buildJsonObject {
            put("email", email.trim().lowercase())
            put("password", password)
        }.toString()
        val resp = post("/api/auth/login", loginBody, authenticated = false)
        token = resp["access_token"]!!.jsonPrimitive.content
        refreshProfile()
    }

    fun refreshProfile() {
        val profile = get("/api/profile")
        userId = profile["id"]!!.jsonPrimitive.content
        playerTag = profile["player_tag"]!!.jsonPrimitive.content
        displayName = profile["display_name"]!!.jsonPrimitive.content
    }

    fun sendFriendRequest(targetTag: String) {
        val body = buildJsonObject {
            put("player_tag", targetTag)
        }.toString()
        post("/api/friends/request", body)
    }

    fun acceptFriendRequest(senderId: String) {
        post("/api/friends/$senderId/accept", null)
    }

    fun blockUser(targetId: String) {
        val body = buildJsonObject {
            put("target_id", targetId)
        }.toString()
        post("/api/safety/block", body)
    }

    fun completeFocusSession(tasksCompleted: Int, lastTaskText: String) {
        val startResp = post("/api/sessions/start", null)
        val sessionId = startResp["session_id"]!!.jsonPrimitive.content
        val finishBody = buildJsonObject {
            put("tasks_completed", tasksCompleted)
            put("last_task_text", lastTaskText)
        }.toString()
        post("/api/sessions/$sessionId/finish", finishBody)
    }

    fun joinRoom(
        activeTaskText: String = "Reading papers",
        completedTasks: Int = 0,
        totalTasks: Int = 1,
        sessionKeySuffix: String = UUID.randomUUID().toString()
    ) {
        leaveRoom()
        val wsBase = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val presenceKey = "$userId:$sessionKeySuffix"
        val encodedName = URLEncoder.encode(displayName, "UTF-8")
        val url = "$wsBase/ws/room?token=$token&presence_key=$presenceKey&name=$encodedName"

        val openLatch = CountDownLatch(1)
        var failure: Throwable? = null

        val socket = httpClient.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    openLatch.countDown()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    failure = t
                    openLatch.countDown()
                }
            }
        )

        check(openLatch.await(10, TimeUnit.SECONDS)) { "Timed out opening peer WebSocket to $url" }
        failure?.let { throw IllegalStateException("Peer WebSocket failed to connect: ${it.message}", it) }

        activeRoomSocket = socket
        activePresenceKey = presenceKey
        updateRoomPresence(activeTaskText, completedTasks, totalTasks)
    }

    fun updateRoomPresence(activeTaskText: String, completedTasks: Int, totalTasks: Int) {
        val pKey = activePresenceKey ?: "$userId:default"
        val payload = buildJsonObject {
            put("type", "presence_update")
            put("presence", buildJsonObject {
                put("id", pKey)
                put("name", displayName)
                put("active_task", activeTaskText)
                put("completed_tasks", completedTasks)
                put("total_tasks", totalTasks)
            })
        }.toString()
        val sent = activeRoomSocket?.send(payload) ?: false
        check(sent) { "Peer room WebSocket is not connected" }
    }

    fun leaveRoom() {
        activeRoomSocket?.close(1000, "Peer leaving E2E room")
        activeRoomSocket = null
        activePresenceKey = null
    }

    fun close() {
        leaveRoom()
    }

    private fun get(path: String): JsonObject {
        val req = Request.Builder()
            .url("$baseUrl$path")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        return executeJson(req)
    }

    private fun post(path: String, body: String?, authenticated: Boolean = true): JsonObject {
        val reqBody = (body ?: "").toRequestBody(jsonMediaType)
        val builder = Request.Builder()
            .url("$baseUrl$path")
            .post(reqBody)
        if (authenticated) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        return executeJson(builder.build())
    }

    private fun patch(path: String, body: String): JsonObject {
        val req = Request.Builder()
            .url("$baseUrl$path")
            .addHeader("Authorization", "Bearer $token")
            .patch(body.toRequestBody(jsonMediaType))
            .build()
        return executeJson(req)
    }

    private fun executeJson(request: Request): JsonObject {
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                "Peer HTTP ${request.method} ${request.url} failed (${response.code}): $raw"
            }
            return if (raw.isBlank()) buildJsonObject { } else json.parseToJsonElement(raw).jsonObject
        }
    }
}

// --- Compose UI E2E Extension Helpers ---

fun ComposeTestRule.waitUntilNodeExists(
    matcher: SemanticsMatcher,
    timeoutMs: Long = 15_000L,
    useUnmergedTree: Boolean = false
) {
    waitUntil(timeoutMs) {
        onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
    }
}

fun ComposeTestRule.waitUntilNodeDoesNotExist(
    matcher: SemanticsMatcher,
    timeoutMs: Long = 15_000L,
    useUnmergedTree: Boolean = false
) {
    waitUntil(timeoutMs) {
        onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().isEmpty()
    }
}

fun ComposeTestRule.signInWithEmailUi(email: String, password: String) {
    waitUntilNodeExists(hasText("Reviewer Access"))
    onNodeWithText("Reviewer Access").performClick()
    waitUntilNodeExists(hasTestTag("login_email_input"))
    onNodeWithTag("login_email_input").performTextInput(email)
    onNodeWithTag("login_password_input").performTextInput(password)
    onNodeWithTag("login_submit_button").performClick()
    waitUntilNodeExists(hasText("Start Focus"))
}

fun ComposeTestRule.openSettingsFromTopBar() {
    waitUntilNodeExists(hasContentDescription("Settings"))
    onNodeWithContentDescription("Settings").performClick()
    waitUntilNodeExists(hasText("Display Name"))
}

fun ComposeTestRule.openOasisSubTab(subTabLabel: String) {
    waitUntilNodeExists(hasText("Oasis"))
    onNodeWithText("Oasis").performClick()
    waitUntilNodeExists(hasText(subTabLabel))
    onNodeWithText(subTabLabel).performClick()
}

fun ComposeTestRule.addTaskInFocusRoom(taskText: String) {
    waitUntilNodeExists(hasTestTag("room_task_input"))
    onNodeWithTag("room_task_input").performTextClearance()
    onNodeWithTag("room_task_input").performTextInput(taskText)
    onNodeWithTag("room_add_task_button").performClick()
}

fun SemanticsNodeInteraction.readDisplayedText(): String {
    val node = fetchSemanticsNode()
    val texts = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
    return texts.joinToString("") { it.text }
}
