package com.example.authapp.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AppPreferences {
    private const val PREFS_NAME = "calculator_auth_secure_prefs"
    private const val KEY_USER_ID = "pref_user_id"
    private const val KEY_USER_EMAIL = "pref_user_email"
    private const val KEY_USER_ROLE = "pref_user_role"
    private const val KEY_FCM_TOKEN = "pref_fcm_token"
    private const val KEY_LINKED_PARENT_ID = "pref_linked_parent_id"

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "CalculatorSecurityKey_v1"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    @Volatile
    private var cachedSecretKey: SecretKey? = null

    // High performance in-memory cache to avoid repeated Keystore AES decrypts
    @Volatile private var memUserId: String? = null
    @Volatile private var memUserEmail: String? = null
    @Volatile private var memUserRole: String? = null
    @Volatile private var memFcmToken: String? = null
    @Volatile private var memLinkedParentId: String? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey? {
        cachedSecretKey?.let { return it }
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val key = if (keyStore.containsAlias(KEY_ALIAS)) {
                (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
            cachedSecretKey = key
            key
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val secretKey = getOrCreateSecretKey() ?: return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            "enc:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            ""
        }
    }

    private fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""
        if (!encryptedText.startsWith("enc:")) return encryptedText // Backward compatibility with unencrypted
        return try {
            val base64Data = encryptedText.removePrefix("enc:")
            val combined = Base64.decode(base64Data, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return ""
            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val secretKey = getOrCreateSecretKey() ?: return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            ""
        }
    }

    fun saveUserSession(context: Context, uid: String, email: String, role: String = "child") {
        memUserId = uid
        memUserEmail = email
        memUserRole = role
        getPrefs(context).edit()
            .putString(KEY_USER_ID, encrypt(uid))
            .putString(KEY_USER_EMAIL, encrypt(email))
            .putString(KEY_USER_ROLE, encrypt(role))
            .apply()
    }

    fun getUserId(context: Context): String {
        memUserId?.let { if (it.isNotEmpty()) return it }
        val value = decrypt(getPrefs(context).getString(KEY_USER_ID, "") ?: "")
        if (value.isNotEmpty()) memUserId = value
        return value
    }

    fun getUserEmail(context: Context): String {
        memUserEmail?.let { if (it.isNotEmpty()) return it }
        val value = decrypt(getPrefs(context).getString(KEY_USER_EMAIL, "") ?: "")
        if (value.isNotEmpty()) memUserEmail = value
        return value
    }

    fun getUserRole(context: Context): String {
        memUserRole?.let { if (it.isNotEmpty()) return it }
        val role = decrypt(getPrefs(context).getString(KEY_USER_ROLE, "") ?: "")
        val finalRole = role.ifEmpty { "child" }
        memUserRole = finalRole
        return finalRole
    }

    fun saveFcmToken(context: Context, token: String) {
        memFcmToken = token
        getPrefs(context).edit().putString(KEY_FCM_TOKEN, encrypt(token)).apply()
    }

    fun getFcmToken(context: Context): String {
        memFcmToken?.let { if (it.isNotEmpty()) return it }
        val value = decrypt(getPrefs(context).getString(KEY_FCM_TOKEN, "") ?: "")
        if (value.isNotEmpty()) memFcmToken = value
        return value
    }

    fun saveLinkedParentId(context: Context, parentId: String) {
        memLinkedParentId = parentId
        getPrefs(context).edit().putString(KEY_LINKED_PARENT_ID, encrypt(parentId)).apply()
    }

    fun getLinkedParentId(context: Context): String {
        memLinkedParentId?.let { if (it.isNotEmpty()) return it }
        val value = decrypt(getPrefs(context).getString(KEY_LINKED_PARENT_ID, "") ?: "")
        if (value.isNotEmpty()) memLinkedParentId = value
        return value
    }

    fun clearSession(context: Context) {
        memUserId = null
        memUserEmail = null
        memUserRole = null
        memFcmToken = null
        memLinkedParentId = null
        getPrefs(context).edit().clear().apply()
    }
}
