package com.example.authapp.data

import android.content.Context

/**
 * Recording is opt-in on the child device. The foreground service still shows
 * a persistent recording notification whenever recording is active.
 */
object CallRecordingPreferences {
    private const val PREFS = "call_recording_preferences"
    private const val KEY_ENABLED = "recording_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
