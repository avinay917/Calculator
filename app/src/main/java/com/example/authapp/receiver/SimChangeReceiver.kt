package com.example.authapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.AppPreferences
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.SecurityAlert
import com.example.authapp.data.SimCardInfo

class SimChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val PREF_LAST_SIM_OPERATOR = "pref_last_sim_operator"
        private const val PREF_LAST_SIM_COUNTRY = "pref_last_sim_country"

        fun checkAndSyncSimState(context: Context) {
            val childId = AppHealthTelemetry.getEffectiveUserId(context)
            if (childId.isEmpty()) return

            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
                val operatorName = tm.simOperatorName.ifEmpty { tm.networkOperatorName }
                val countryIso = tm.simCountryIso.uppercase()
                val simState = when (tm.simState) {
                    TelephonyManager.SIM_STATE_READY -> "READY"
                    TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
                    TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
                    TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
                    TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
                    else -> "UNKNOWN"
                }

                val prefs = context.getSharedPreferences("sim_prefs", Context.MODE_PRIVATE)
                val lastOperator = prefs.getString(PREF_LAST_SIM_OPERATOR, null)
                val lastCountry = prefs.getString(PREF_LAST_SIM_COUNTRY, null)

                val simInfo = SimCardInfo(
                    simSlot = 0,
                    operatorName = operatorName,
                    countryIso = countryIso,
                    simState = simState,
                    lastUpdated = System.currentTimeMillis()
                )
                FirebaseRepository.saveSimCardInfo(childId, simInfo)

                if (lastOperator != null && operatorName.isNotEmpty() && lastOperator != operatorName) {
                    val alert = SecurityAlert(
                        type = "SIM_CHANGED",
                        title = "SIM Card Changed Alert",
                        message = "SIM card change detected on child device. New carrier: $operatorName ($countryIso). Previous: $lastOperator.",
                        timestamp = System.currentTimeMillis(),
                        severity = "CRITICAL"
                    )
                    FirebaseRepository.pushSecurityAlert(childId, alert)
                }

                if (operatorName.isNotEmpty()) {
                    prefs.edit()
                        .putString(PREF_LAST_SIM_OPERATOR, operatorName)
                        .putString(PREF_LAST_SIM_COUNTRY, countryIso)
                        .apply()
                }
            } catch (e: Exception) {
                // Ignore permission or telephony errors
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        checkAndSyncSimState(context)
    }
}
