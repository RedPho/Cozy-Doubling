package com.grepho.cozydoubling.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface TokenStore {
    fun saveToken(token: String)
    fun loadToken(): String?
    fun clearToken()
}

class InMemoryTokenStore(initialToken: String? = null) : TokenStore {
    @Volatile
    private var token: String? = initialToken

    override fun saveToken(token: String) {
        this.token = token
    }

    override fun loadToken(): String? = token

    override fun clearToken() {
        this.token = null
    }
}

/**
 * Encrypts the persisted auth JWT using an AES-256 GCM key stored in AndroidKeyStore
 * before writing to private SharedPreferences.
 */
class EncryptedTokenStore(context: Context) : TokenStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun saveToken(token: String) {
        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
            val encoded = "${Base64.encodeToString(iv, Base64.NO_WRAP)}:${
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            }"
            prefs.edit().putString(KEY_ENCRYPTED_TOKEN, encoded).apply()
        } catch (e: Exception) {
            e.printStackTrace()
            clearToken()
        }
    }

    @Synchronized
    override fun loadToken(): String? {
        val raw = prefs.getString(KEY_ENCRYPTED_TOKEN, null) ?: return null
        return try {
            val parts = raw.split(":")
            if (parts.size != 2) {
                clearToken()
                return null
            }
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            clearToken()
            null
        }
    }

    @Synchronized
    override fun clearToken() {
        prefs.edit().remove(KEY_ENCRYPTED_TOKEN).commit()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "cozy_doubling_auth_token_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PREFS_NAME = "cozy_doubling_secure_auth"
        private const val KEY_ENCRYPTED_TOKEN = "encrypted_auth_token"
    }
}
