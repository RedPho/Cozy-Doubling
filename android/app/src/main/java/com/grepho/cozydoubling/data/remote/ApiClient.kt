package com.grepho.cozydoubling.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException(message)

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

    private fun extractErrorMessage(code: Int, bodyString: String): String {
        try {
            if (bodyString.contains("\"detail\"")) {
                val jsonElement = json.parseToJsonElement(bodyString)
                if (jsonElement is kotlinx.serialization.json.JsonObject) {
                    val detail = jsonElement["detail"]
                    if (detail is kotlinx.serialization.json.JsonPrimitive && detail.isString) {
                        return detail.content
                    }
                }
            }
        } catch (_: Exception) {}
        return if (bodyString.isNotBlank()) bodyString else "HTTP $code error"
    }

    private fun mapNetworkException(path: String, e: Exception): Exception {
        println("DEBUG: ApiClient - $path exception: ${e.message}")
        return if (e is java.net.ConnectException || e is java.net.SocketTimeoutException || e.message?.contains("failed to connect", ignoreCase = true) == true) {
            IOException("Cannot connect to server at $baseUrl. Please ensure backend is running (python backend/run.py).", e)
        } else {
            e
        }
    }

    suspend fun <T> get(path: String, deserializer: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder(path).get().build()
            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val err = extractErrorMessage(response.code, bodyString)
                println("DEBUG: ApiClient - GET $path HTTP ${response.code}: $err")
                Result.failure(ApiException(response.code, err))
            } else {
                Result.success(deserializer(bodyString))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException("GET $path", e))
        }
    }

    suspend fun <T> post(path: String, jsonBody: String? = null, deserializer: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val body = (jsonBody ?: "").toRequestBody(JSON_MEDIA_TYPE)
            val request = newRequestBuilder(path).post(body).build()
            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val err = extractErrorMessage(response.code, bodyString)
                println("DEBUG: ApiClient - POST $path HTTP ${response.code}: $err")
                Result.failure(ApiException(response.code, err))
            } else {
                Result.success(deserializer(bodyString))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException("POST $path", e))
        }
    }

    suspend fun <T> patch(path: String, jsonBody: String, deserializer: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
            val request = newRequestBuilder(path).patch(body).build()
            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val err = extractErrorMessage(response.code, bodyString)
                println("DEBUG: ApiClient - PATCH $path HTTP ${response.code}: $err")
                Result.failure(ApiException(response.code, err))
            } else {
                Result.success(deserializer(bodyString))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException("PATCH $path", e))
        }
    }

    suspend fun <T> delete(path: String, deserializer: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder(path).delete().build()
            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val err = extractErrorMessage(response.code, bodyString)
                println("DEBUG: ApiClient - DELETE $path HTTP ${response.code}: $err")
                Result.failure(ApiException(response.code, err))
            } else {
                Result.success(deserializer(bodyString))
            }
        } catch (e: Exception) {
            Result.failure(mapNetworkException("DELETE $path", e))
        }
    }

    private val webSocketClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .build()
    }

    fun openWebSocket(path: String, listener: WebSocketListener): WebSocket {
        val wsBase = baseUrl.replace("http://", "ws://").replace("https://", "wss://").trimEnd('/')
        val cleanPath = path.trimStart('/')
        val fullUrl = "$wsBase/$cleanPath"

        val request = Request.Builder()
            .url(fullUrl)
            .build()
        return webSocketClient.newWebSocket(request, listener)
    }
}
