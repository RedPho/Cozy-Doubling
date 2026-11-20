package com.grepho.cozydoubling.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(
    var baseUrl: String = "http://10.0.2.2:8000",
    customOkHttpClient: OkHttpClient? = null
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    var authToken: String? = null

    val okHttpClient: OkHttpClient = customOkHttpClient ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun newRequestBuilder(path: String): Request.Builder {
        val fullUrl = if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            val cleanBase = baseUrl.trimEnd('/')
            val cleanPath = path.trimStart('/')
            "$cleanBase/$cleanPath"
        }

        val builder = Request.Builder().url(fullUrl)
        authToken?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    suspend fun <T> get(path: String, deserializer: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder(path).get().build()
            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Result.failure(IOException("HTTP ${response.code}: $bodyString"))
            } else {
                Result.success(deserializer(bodyString))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun <T> post(path: String, jsonBody: String? = null, deserializer: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val body = (jsonBody ?: "").toRequestBody(JSON_MEDIA_TYPE)
            val request = newRequestBuilder(path).post(body).build()
            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Result.failure(IOException("HTTP ${response.code}: $bodyString"))
            } else {
                Result.success(deserializer(bodyString))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun <T> patch(path: String, jsonBody: String, deserializer: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
            val request = newRequestBuilder(path).patch(body).build()
            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Result.failure(IOException("HTTP ${response.code}: $bodyString"))
            } else {
                Result.success(deserializer(bodyString))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun <T> delete(path: String, deserializer: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder(path).delete().build()
            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Result.failure(IOException("HTTP ${response.code}: $bodyString"))
            } else {
                Result.success(deserializer(bodyString))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openWebSocket(path: String, listener: WebSocketListener): WebSocket {
        val wsBase = baseUrl.replace("http://", "ws://").replace("https://", "wss://").trimEnd('/')
        val cleanPath = path.trimStart('/')
        val fullUrl = "$wsBase/$cleanPath"

        val request = Request.Builder()
            .url(fullUrl)
            .build()
        return okHttpClient.newWebSocket(request, listener)
    }
}
