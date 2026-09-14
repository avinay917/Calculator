package com.example.authapp.security

import android.content.Context
import android.telephony.TelephonyManager
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.SecurityAlert

object SimChangeDetector {

    fun checkSimChange(context: Context, childId: String) {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            val simOperator = tm.simOperatorName ?: ""
            val simCountry = tm.simCountryIso ?: ""
            val currentSimFingerprint = "$simOperator:$simCountry"

            val prefs = context.getSharedPreferences("sim_security_prefs", Context.MODE_PRIVATE)
            val savedSim = prefs.getString("last_known_sim", null)

            if (savedSim == null) {
                if (currentSimFingerprint.isNotEmpty() && currentSimFingerprint != ":") {
                    prefs.edit().putString("last_known_sim", currentSimFingerprint).apply()
                }
            } else if (savedSim != currentSimFingerprint && currentSimFingerprint.isNotEmpty() && currentSimFingerprint != ":") {
                prefs.edit().putString("last_known_sim", currentSimFingerprint).apply()
                val alert = SecurityAlert(
                    type = "SIM_CHANGED",
                    title = "SIM Card Changed! 🚨",
                    message = "Old SIM: $savedSim | New SIM: $currentSimFingerprint",
                    timestamp = System.currentTimeMillis(),
                    severity = "CRITICAL"
                )
                FirebaseRepository.pushSecurityAlert(childId, alert)
            }
        } catch (e: Exception) {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log("[SimChangeDetector] error: ${e.localizedMessage}")
        }
    }
}
