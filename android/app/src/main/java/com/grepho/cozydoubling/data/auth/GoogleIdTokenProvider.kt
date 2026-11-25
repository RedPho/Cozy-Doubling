package com.grepho.cozydoubling.data.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.grepho.cozydoubling.core.auth.AuthCancelledException
import java.util.UUID

/**
 * Obtains a Google ID token that the backend verifies at POST /api/auth/google.
 * Deliberately not a `fun interface`: SAM-converted suspend lambdas returning kotlin.Result are
 * mis-boxed by the Kotlin compiler (ClassCastException at runtime).
 */
interface GoogleIdTokenProvider {
    suspend fun requestIdToken(activity: Activity): Result<String>
}

/** Fails closed; used when no provider is configured. */
object UnconfiguredGoogleIdTokenProvider : GoogleIdTokenProvider {
    override suspend fun requestIdToken(activity: Activity): Result<String> =
        Result.failure(IllegalStateException("Google Sign-In is not configured for this build."))
}

/**
 * Production provider: Credential Manager "Sign in with Google" bottom sheet.
 * [webClientId] is the OAuth 2.0 *web* client ID; the backend accepts tokens whose audience matches it.
 */
class CredentialManagerGoogleIdTokenProvider(
    private val webClientId: String
) : GoogleIdTokenProvider {

    override suspend fun requestIdToken(activity: Activity): Result<String> {
        if (webClientId.isBlank()) {
            return Result.failure(IllegalStateException("Google Sign-In is not configured for this build."))
        }
        val option = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(UUID.randomUUID().toString())
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Result.success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type returned by Google Sign-In."))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(AuthCancelledException())
        } catch (e: NoCredentialException) {
            Result.failure(IllegalStateException("No Google account is available on this device."))
        } catch (e: GetCredentialException) {
            Result.failure(IllegalStateException("Google Sign-In failed. Please try again.", e))
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(IllegalStateException("Google Sign-In returned an invalid credential.", e))
        }
    }
}

/**
 * Debug-only provider for the local FastAPI backend and E2E runs (ENVIRONMENT=development accepts
 * "mock:google:" tokens). Never wired into release builds.
 */
class DebugMockGoogleIdTokenProvider : GoogleIdTokenProvider {
    // Random per install so two emulators never resolve to the same mock account.
    private val deviceId: String by lazy { UUID.randomUUID().toString().take(8) }

    override suspend fun requestIdToken(activity: Activity): Result<String> =
        Result.success("mock:google:dev_$deviceId:Cozy Doubler $deviceId:cozy_$deviceId@google.com")
}
