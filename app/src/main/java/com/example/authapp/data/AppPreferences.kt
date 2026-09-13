package com.example.authapp.data

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    private const val PREFS_NAME = "calculator_auth_prefs"
    private const val KEY_USER_ID = "pref_user_id"
    private const val KEY_USER_EMAIL = "pref_user_email"
    private const val KEY_USER_ROLE = "pref_user_role"
    private const val KEY_FCM_TOKEN = "pref_fcm_token"
    private const val KEY_LINKED_PARENT_ID = "pref_linked_parent_id"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveUserSession(context: Context, uid: String, email: String, role: String = "child") {
        getPrefs(context).edit()
            .putString(KEY_USER_ID, uid)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_ROLE, role)
            .apply()
    }

    fun getUserId(context: Context): String {
        return getPrefs(context).getString(KEY_USER_ID, "") ?: ""
    }

    fun getUserEmail(context: Context): String {
        return getPrefs(context).getString(KEY_USER_EMAIL, "") ?: ""
    }

    fun getUserRole(context: Context): String {
        return getPrefs(context).getString(KEY_USER_ROLE, "child") ?: "child"
    }

    fun saveFcmToken(context: Context, token: String) {
        getPrefs(context).edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getFcmToken(context: Context): String {
        return getPrefs(context).getString(KEY_FCM_TOKEN, "") ?: ""
    }

    fun saveLinkedParentId(context: Context, parentId: String) {
        getPrefs(context).edit().putString(KEY_LINKED_PARENT_ID, parentId).apply()
    }

    fun getLinkedParentId(context: Context): String {
        return getPrefs(context).getString(KEY_LINKED_PARENT_ID, "") ?: ""
    }

    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
