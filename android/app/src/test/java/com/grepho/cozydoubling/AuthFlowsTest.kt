package com.grepho.cozydoubling

import android.app.Activity
import com.grepho.cozydoubling.core.auth.AuthCancelledException
import com.grepho.cozydoubling.core.auth.AuthInputValidator
import com.grepho.cozydoubling.core.auth.AuthStatus
import com.grepho.cozydoubling.data.auth.GoogleIdTokenProvider
import com.grepho.cozydoubling.data.fake.FakeAuthRepository
import com.grepho.cozydoubling.data.local.InMemoryTokenStore
import com.grepho.cozydoubling.data.remote.ApiClient
import com.grepho.cozydoubling.data.remote.RemoteAuthRepository
import com.grepho.cozydoubling.data.remote.RemoteBillingRepository
import com.grepho.cozydoubling.data.remote.RemoteProfileRepository
import com.grepho.cozydoubling.features.auth.LoginError
import com.grepho.cozydoubling.features.auth.LoginViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFlowsTest {

    private data class Call(val method: String, val path: String, val body: String)

    /** ApiClient backed by an in-process interceptor; [respond] returns (code, json) per call. */
    private fun fakeApi(calls: MutableList<Call>, respond: (Call) -> Pair<Int, String>): ApiClient {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val body = request.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() } ?: ""
            val call = Call(request.method, request.url.encodedPath, body)
            calls += call
            val (code, json) = respond(call)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        return ApiClient(baseUrl = "http://mockserver:8000", customOkHttpClient = client)
    }

    private fun providerReturning(result: Result<String>) = object : GoogleIdTokenProvider {
        override suspend fun requestIdToken(activity: Activity): Result<String> = result
    }

    private val meJson = """{"id":"user_1","display_name":"Sam","player_tag":"ABCDEFGH"}"""
    private val tokenJson = """{"access_token":"jwt_1","token_type":"bearer"}"""

    // ── Input validation ─────────────────────────────────────────────────────

    @Test
    fun validator_acceptsValidInputAndRejectsBadInput() {
        assertNull(AuthInputValidator.validateSignIn("  Sam@Example.COM ", "x"))
        assertEquals(AuthInputValidator.Problem.EMAIL_INVALID, AuthInputValidator.validateSignIn("sam@", "secret"))
        assertEquals(AuthInputValidator.Problem.PASSWORD_EMPTY, AuthInputValidator.validateSignIn("sam@x.io", ""))

        assertNull(AuthInputValidator.validateRegistration("sam@x.io", "longenough", "longenough"))
        assertEquals(
            AuthInputValidator.Problem.PASSWORD_TOO_SHORT,
            AuthInputValidator.validateRegistration("sam@x.io", "short", "short")
        )
        assertEquals(
            AuthInputValidator.Problem.PASSWORDS_DIFFER,
            AuthInputValidator.validateRegistration("sam@x.io", "longenough", "longenougH")
        )
        assertEquals("sam@example.com", AuthInputValidator.normalizeEmail("  Sam@Example.COM "))
    }

    @Test
    fun loginViewModel_rejectsInvalidRegistrationWithoutCallingRepository() {
        val repo = FakeAuthRepository(initialStatus = AuthStatus.NotAuthenticated)
        val vm = LoginViewModel(repo)

        vm.register("sam@x.io", "short", "short", "")
        assertEquals(LoginError.Resource(R.string.login_error_password_short), vm.uiState.value.error)
        assertTrue(repo.authStatus.value is AuthStatus.NotAuthenticated)

        vm.clearError()
        vm.signInWithEmail("not-an-email", "whatever")
        assertEquals(LoginError.Resource(R.string.login_error_email_invalid), vm.uiState.value.error)
    }

    // ── RemoteAuthRepository ─────────────────────────────────────────────────

    @Test
    fun signIn_withUnknownAccount_failsAndNeverAutoRegisters() = runBlocking {
        val calls = mutableListOf<Call>()
        val api = fakeApi(calls) { call ->
            when (call.path) {
                "/api/auth/login" -> 401 to """{"detail":"Invalid email or password"}"""
                else -> 200 to tokenJson
            }
        }
        val repo = RemoteAuthRepository(api, InMemoryTokenStore())

        val result = repo.signInWithEmail("new@cozy.io", "Password123")

        assertTrue(result.isFailure)
        assertEquals("Invalid email or password", result.exceptionOrNull()?.message)
        assertEquals(listOf("/api/auth/login"), calls.map { it.path })
        assertTrue(repo.authStatus.value is AuthStatus.NotAuthenticated)
    }

    @Test
    fun register_sendsNormalizedEmailAndVerbatimPassword_andPersistsToken() = runBlocking {
        val calls = mutableListOf<Call>()
        val api = fakeApi(calls) { call ->
            when (call.path) {
                "/api/auth/register" -> 200 to tokenJson
                "/api/auth/me" -> 200 to meJson
                else -> 404 to "{}"
            }
        }
        val store = InMemoryTokenStore()
        val repo = RemoteAuthRepository(api, store)

        val result = repo.registerWithEmail(" New@Cozy.IO ", "  spaced pass  ", " Sam ")

        assertTrue(result.isSuccess)
        val body = Json.parseToJsonElement(calls.first { it.path == "/api/auth/register" }.body).jsonObject
        assertEquals("new@cozy.io", body["email"]!!.jsonPrimitive.content)
        assertEquals("  spaced pass  ", body["password"]!!.jsonPrimitive.content)
        assertEquals("Sam", body["display_name"]!!.jsonPrimitive.content)
        assertEquals("jwt_1", store.loadToken())
        assertEquals(AuthStatus.Authenticated("user_1"), repo.authStatus.value)
    }

    @Test
    fun googleSignIn_postsProviderTokenToBackend() = runBlocking {
        val calls = mutableListOf<Call>()
        val api = fakeApi(calls) { call ->
            when (call.path) {
                "/api/auth/google" -> 200 to tokenJson
                "/api/auth/me" -> 200 to meJson
                else -> 404 to "{}"
            }
        }
        val provider = providerReturning(Result.success("google.id.token"))
        val repo = RemoteAuthRepository(api, InMemoryTokenStore(), googleIdTokenProvider = provider)

        assertTrue(repo.signInWithGoogle(Activity()).isSuccess)
        val body = Json.parseToJsonElement(calls.first { it.path == "/api/auth/google" }.body).jsonObject
        assertEquals("google.id.token", body["id_token"]!!.jsonPrimitive.content)
    }

    @Test
    fun googleSignIn_cancelledOrUnconfigured_makesNoNetworkCall() = runBlocking {
        val calls = mutableListOf<Call>()
        val api = fakeApi(calls) { 200 to tokenJson }

        val cancelled = RemoteAuthRepository(
            api, InMemoryTokenStore(), googleIdTokenProvider = providerReturning(Result.failure(AuthCancelledException()))
        ).signInWithGoogle(Activity())
        assertTrue(cancelled.exceptionOrNull() is AuthCancelledException)

        // Default provider fails closed (no mock tokens unless explicitly wired for debug).
        val unconfigured = RemoteAuthRepository(api, InMemoryTokenStore()).signInWithGoogle(Activity())
        assertTrue(unconfigured.isFailure)
        assertTrue(calls.isEmpty())
    }

    // ── Account deletion ─────────────────────────────────────────────────────

    @Test
    fun deleteAccount_serverFailure_keepsUserSignedIn() = runBlocking {
        val calls = mutableListOf<Call>()
        val api = fakeApi(calls) { call ->
            when {
                call.path == "/api/auth/login" -> 200 to tokenJson
                call.path == "/api/auth/me" || call.path == "/api/profile" -> 200 to meJson
                call.method == "DELETE" && call.path == "/api/auth/account" -> 500 to """{"detail":"Database unavailable"}"""
                else -> 200 to """{"ok":true}"""
            }
        }
        val store = InMemoryTokenStore()
        val auth = RemoteAuthRepository(api, store)
        assertTrue(auth.signInWithEmail("sam@cozy.io", "Password123").isSuccess)
        val profile = RemoteProfileRepository(api, auth)

        val result = profile.deleteAccount()

        assertTrue(result.isFailure)
        assertEquals(AuthStatus.Authenticated("user_1"), auth.authStatus.value)
        assertEquals("jwt_1", store.loadToken())
        assertFalse(calls.any { it.path == "/api/auth/logout" })
    }

    // ── Billing placeholder ──────────────────────────────────────────────────

    @Test
    fun billing_withoutStoreIntegration_offersNothingAndNeverGrantsSupporter() = runBlocking {
        val calls = mutableListOf<Call>()
        val api = fakeApi(calls) { 200 to meJson }
        val auth = RemoteAuthRepository(api, InMemoryTokenStore())
        val billing = RemoteBillingRepository(api, RemoteProfileRepository(api, auth), simulatePurchases = false)

        assertTrue(billing.offerings.value?.availablePackages.orEmpty().isEmpty())
        assertFalse(billing.purchase(Activity(), "pass_monthly"))
        assertFalse(calls.any { it.path == "/api/subscriptions/sync" && it.body.contains("is_supporter") })
    }
}
