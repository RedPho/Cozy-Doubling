package com.grepho.cozydoubling.e2e

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Shared E2E test harness, multi-device HTTP coordinator client, and backend external-event helper.
 *
 * Lives strictly in androidTest so production Android UI/domain code remains completely
 * decoupled from backend networking and test scaffolding.
 */
object E2eTestHarness {

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

    fun currentUserId(): String =
        ServiceLocator.authRepository.getCurrentUserId()
            ?: error("Expected authenticated userId in ServiceLocator")

    fun runShellCommand(cmd: String) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(cmd)
            .use { /* wait for command pipe to open and close cleanly */ }
    }
}

/**
 * Test-only HTTP client talking to the E2eCoordinator server hosted inside scripts/run_e2e.py.
 * Synchronizes real Android devices across /kv and /barrier endpoints without adding any test
 * endpoints to the production FastAPI backend.
 */
object E2eCoordinator {

    private val textMediaType = "text/plain; charset=utf-8".toMediaType()
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val coordUrl: String
        get() = InstrumentationRegistry.getArguments().getString("coordUrl") ?: "http://127.0.0.1:8011"

    private val runId: String
        get() = InstrumentationRegistry.getArguments().getString("runId") ?: "default"

    fun publish(key: String, value: String) {
        val req = Request.Builder()
            .url("$coordUrl/kv/$runId/$key")
            .post(value.toRequestBody(textMediaType))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "Coordinator publish($key) failed: HTTP ${resp.code}" }
        }
    }

    fun awaitValue(key: String, timeoutSec: Int = 60): String {
        val req = Request.Builder()
            .url("$coordUrl/kv/$runId/$key?timeout=$timeoutSec")
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            check(resp.isSuccessful) { "Coordinator awaitValue($key) failed: HTTP ${resp.code} ($body)" }
            return body
        }
    }

    fun arrive(barrier: String, count: Int, timeoutSec: Int = 60) {
        val req = Request.Builder()
            .url("$coordUrl/barrier/$runId/$barrier/$count?timeout=$timeoutSec")
            .post("".toRequestBody(textMediaType))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            check(resp.isSuccessful) { "Coordinator arrive($barrier/$count) failed: HTTP ${resp.code} ($body)" }
        }
    }

    fun abort() {
        try {
            val req = Request.Builder()
                .url("$coordUrl/abort/$runId")
                .post("".toRequestBody(textMediaType))
                .build()
            httpClient.newCall(req).execute().close()
        } catch (_: Exception) {
            // Best-effort fail-fast signal
        }
    }
}

/**
 * Notifies E2eCoordinator to abort blocked peer devices immediately if this device's test fails.
 */
class AbortCoordinatorOnFailureRule : TestWatcher() {
    override fun failed(e: Throwable?, description: Description?) {
        E2eCoordinator.abort()
    }
}

/**
 * Helper for single-device E2E prerequisites or external server webhook events (never simulates
 * another user).
 */
object BackendTestHelper {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun completeRealSessionViaApi(
        email: String,
        password: String,
        tasksCompleted: Int,
        lastTaskText: String = "Deep Focus Session"
    ) {
        val loginBody = buildJsonObject {
            put("email", email.trim().lowercase())
            put("password", password)
        }.toString()
        val loginResp = executeJson(
            Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/auth/login")
                .post(loginBody.toRequestBody(jsonMediaType))
                .build()
        )
        val token = loginResp["access_token"]!!.jsonPrimitive.content

        val startResp = executeJson(
            Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/sessions/start")
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody(jsonMediaType))
                .build()
        )
        val sessionId = startResp["session_id"]!!.jsonPrimitive.content

        val finishBody = buildJsonObject {
            put("tasks_completed", tasksCompleted)
            put("last_task_text", lastTaskText)
        }.toString()
        executeJson(
            Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/sessions/$sessionId/finish")
                .addHeader("Authorization", "Bearer $token")
                .post(finishBody.toRequestBody(jsonMediaType))
                .build()
        )
    }

    /** Registers an email/password account; tolerates "Email already registered". */
    fun ensureEmailAccountViaApi(email: String, password: String) {
        val body = buildJsonObject {
            put("email", email.trim().lowercase())
            put("password", password)
        }.toString()
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}/api/auth/register")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful || raw.contains("already registered", ignoreCase = true)) {
                "Backend registration for $email failed (${response.code}): $raw"
            }
        }
    }

    // Matches the backend's development default for REVENUECAT_WEBHOOK_AUTH.
    private const val DEV_WEBHOOK_AUTH = "cozy_rc_secret_key"

    fun triggerSupporterExpirationWebhook(userId: String) {
        val payload = buildJsonObject {
            put(
                "event",
                buildJsonObject {
                    put("type", "EXPIRATION")
                    put("app_user_id", userId)
                }
            )
        }.toString()
        executeJson(
            Request.Builder()
                .url("${BuildConfig.API_BASE_URL}/api/subscriptions/webhook")
                .addHeader("Authorization", "Bearer $DEV_WEBHOOK_AUTH")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
        )
    }

    private fun executeJson(request: Request): JsonObject {
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                "Backend HTTP ${request.method} ${request.url} failed (${response.code}): $raw"
            }
            return if (raw.isBlank()) buildJsonObject { } else json.parseToJsonElement(raw).jsonObject
        }
    }
}

// --- Compose UI E2E Extension Helpers ---

fun ComposeTestRule.waitUntilNodeExists(
    matcher: SemanticsMatcher,
    timeoutMs: Long = 20_000L,
    useUnmergedTree: Boolean = false
) {
    waitUntil(timeoutMs) {
        onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().isNotEmpty()
    }
}

fun ComposeTestRule.waitUntilNodeDoesNotExist(
    matcher: SemanticsMatcher,
    timeoutMs: Long = 20_000L,
    useUnmergedTree: Boolean = false
) {
    waitUntil(timeoutMs) {
        onAllNodes(matcher, useUnmergedTree).fetchSemanticsNodes().isEmpty()
    }
}

/** Visible on the Login screen; used to detect that the user is signed out. */
const val LOGIN_SCREEN_MARKER = "Continue with email"

/**
 * Signs in through the email dialog. The app no longer auto-registers unknown emails, so the
 * account is first created through the backend API (a no-op if it already exists).
 */
fun ComposeTestRule.signInWithEmailUi(email: String, password: String) {
    BackendTestHelper.ensureEmailAccountViaApi(email, password)
    waitUntilNodeExists(hasText(LOGIN_SCREEN_MARKER))
    onNodeWithText(LOGIN_SCREEN_MARKER).performClick()
    waitUntilNodeExists(hasTestTag("login_email_input"))
    onNodeWithTag("login_email_input").performTextInput(email)
    onNodeWithTag("login_password_input").performTextInput(password)
    onNodeWithTag("login_submit_button").performClick()
    waitUntilNodeExists(hasText("Start Focus"))
}

/** Creates a new account through the in-app registration form ([AUTH-08]). */
fun ComposeTestRule.registerWithEmailUi(email: String, password: String, displayName: String = "") {
    waitUntilNodeExists(hasText(LOGIN_SCREEN_MARKER))
    onNodeWithText(LOGIN_SCREEN_MARKER).performClick()
    waitUntilNodeExists(hasTestTag("login_mode_toggle"))
    onNodeWithTag("login_mode_toggle").performClick()
    waitUntilNodeExists(hasTestTag("register_confirm_password_input"))
    onNodeWithTag("login_email_input").performTextInput(email)
    if (displayName.isNotEmpty()) onNodeWithTag("register_display_name_input").performTextInput(displayName)
    onNodeWithTag("login_password_input").performTextInput(password)
    onNodeWithTag("register_confirm_password_input").performTextInput(password)
    onNodeWithTag("login_submit_button").performClick()
    waitUntilNodeExists(hasText("Start Focus"))
}

fun ComposeTestRule.openSettingsFromTopBar() {
    waitUntilNodeExists(hasContentDescription("Settings"))
    onNodeWithContentDescription("Settings").performClick()
    waitUntilNodeExists(hasText("Display Name"))
}

fun ComposeTestRule.updateDisplayNameFromHome(newName: String) {
    openSettingsFromTopBar()
    onNodeWithText("Display Name").performClick()
    waitUntilNodeExists(hasTestTag("settings_username_input"))
    onNodeWithTag("settings_username_input").performTextClearance()
    onNodeWithTag("settings_username_input").performTextInput(newName)
    onNodeWithText("Save").performClick()
    waitUntilNodeExists(hasText(newName))
    onNodeWithContentDescription("OK").performClick()
    waitUntilNodeExists(hasText("Start Focus"))
}

fun ComposeTestRule.signOutFromHome() {
    openSettingsFromTopBar()
    onNodeWithText("Sign Out").performScrollTo().performClick()
    waitUntilNodeExists(hasText(LOGIN_SCREEN_MARKER))
}

fun ComposeTestRule.openOasisSubTab(subTabLabel: String) {
    waitUntilNodeExists(hasText("Oasis"))
    onNodeWithText("Oasis").performClick()
    waitUntilNodeExists(hasText(subTabLabel))
    onNodeWithText(subTabLabel).performClick()
}

fun ComposeTestRule.refreshFriendsTab() {
    onNodeWithText("Journey").performClick()
    onNodeWithText("Friends").performClick()
}

fun ComposeTestRule.readOwnConnectCodeInFriends(): String {
    waitUntilNodeExists(hasText("YOUR CONNECT CODE"))
    waitUntil(20_000L) {
        onNodeWithTag("friends_connect_code", useUnmergedTree = true).readDisplayedText().length == 8
    }
    return onNodeWithTag("friends_connect_code", useUnmergedTree = true).readDisplayedText()
}

fun ComposeTestRule.sendFriendRequestViaDialog(tagInput: String) {
    waitUntilNodeExists(hasContentDescription("Add Friend"))
    onNodeWithContentDescription("Add Friend").performClick()
    waitUntilNodeExists(hasTestTag("friends_add_input"))
    onNodeWithTag("friends_add_input").performTextInput(tagInput)
    onNodeWithText("Send Request").performClick()
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
