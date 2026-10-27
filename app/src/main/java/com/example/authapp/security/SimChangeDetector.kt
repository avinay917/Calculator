package com.example.authapp.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.authapp.analytics.AppHealthTelemetry
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.data.SecurityAlert
import com.example.authapp.data.SimCardInfo
import com.google.firebase.crashlytics.FirebaseCrashlytics

object SimChangeDetector {

    private const val PREFS_NAME = "sim_security_prefs"
    private const val KEY_LAST_FINGERPRINT = "last_known_sim_fingerprint"
    private const val KEY_LAST_CARRIER = "last_known_carrier"
    private const val KEY_LAST_SIM_COUNT = "last_known_sim_count"
    private const val KEY_LAST_ALERT_TIME = "last_sim_alert_timestamp"
    private const val ALERT_THROTTLE_MS = 3_600_000L // 1 hour throttle to prevent duplicate spam

    fun checkSimChange(context: Context, childId: String = "") {
        val effectiveChildId = if (childId.isNotEmpty()) childId else AppHealthTelemetry.getEffectiveUserId(context)
        if (effectiveChildId.isEmpty()) return

        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            val hasPhonePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            var currentFingerprint = ""
            var currentCarrier = ""
            var currentCountry = tm.simCountryIso?.uppercase() ?: ""
            val rawSimState = tm.simState
            var currentSimState = when (rawSimState) {
                TelephonyManager.SIM_STATE_READY -> "READY"
                TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
                else -> "UNKNOWN"
            }
            var simCount = 0
            var primarySlot = 0

            // 1. Dual-SIM & SubscriptionManager (Android 5.1+ / API 22+)
            if (hasPhonePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeSubs: List<SubscriptionInfo>? = try {
                    sm?.activeSubscriptionInfoList
                } catch (e: SecurityException) {
                    null
                }

                if (!activeSubs.isNullOrEmpty()) {
                    simCount = activeSubs.size
                    val sbFingerprint = StringBuilder()
                    val carriers = mutableListOf<String>()

                    for (sub in activeSubs) {
                        val slot = sub.simSlotIndex
                        val subId = sub.subscriptionId
                        val carrierName = sub.carrierName?.toString()?.trim()
                            ?.ifEmpty { sub.displayName?.toString()?.trim() }
                            ?: ""

                        if (carrierName.isNotEmpty()) {
                            carriers.add("SIM ${slot + 1}: $carrierName")
                        }
                        sbFingerprint.append("slot:$slot|id:$subId|carrier:$carrierName;")
                    }

                    currentFingerprint = sbFingerprint.toString()
                    currentCarrier = carriers.joinToString(", ")
                    val firstSub = activeSubs[0]
                    primarySlot = firstSub.simSlotIndex
                    if (firstSub.countryIso?.isNotEmpty() == true) {
                        currentCountry = firstSub.countryIso.uppercase()
                    }
                    currentSimState = "READY"
                }
            }

            // 2. Fallback to TelephonyManager if SubscriptionManager is unavailable
            val opName = tm.simOperatorName.ifEmpty { tm.networkOperatorName }.trim()
            if (currentFingerprint.isEmpty()) {
                currentCarrier = opName
                val isAbsent = rawSimState == TelephonyManager.SIM_STATE_ABSENT
                simCount = if (isAbsent || (opName.isEmpty() && currentCountry.isEmpty())) 0 else 1
                val opCode = tm.simOperator.ifEmpty { tm.networkOperator }
                currentFingerprint = "slot:0|op:$opCode|carrier:$opName|country:$currentCountry"
            }

            // Ignore temporary transitions (airplane mode toggle, radio re-calibration, screen-off state)
            if (currentCarrier.isEmpty() && currentCountry.isEmpty() && currentSimState != "ABSENT") {
                return
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastFingerprint = prefs.getString(KEY_LAST_FINGERPRINT, null)
            val lastCarrier = prefs.getString(KEY_LAST_CARRIER, "") ?: ""
            val lastSimCount = prefs.getInt(KEY_LAST_SIM_COUNT, -1)
            val lastAlertTime = prefs.getLong(KEY_LAST_ALERT_TIME, 0L)

            val now = System.currentTimeMillis()

            // 3. Always update /sim_info node for Parent UI
            val simInfo = SimCardInfo(
                simSlot = primarySlot,
                operatorName = if (currentSimState == "ABSENT") "No SIM Card" else currentCarrier.ifEmpty { opName.ifEmpty { "Unknown Carrier" } },
                countryIso = currentCountry,
                simSerialNumber = currentFingerprint.take(100),
                simState = currentSimState,
                lastUpdated = now
            )
            FirebaseRepository.saveSimCardInfo(effectiveChildId, simInfo)

            // 4. Alert Logic: Initial Registration vs Change / Removal
            if (lastFingerprint == null) {
                // First-time calibration / boot baseline
                prefs.edit()
                    .putString(KEY_LAST_FINGERPRINT, currentFingerprint)
                    .putString(KEY_LAST_CARRIER, currentCarrier)
                    .putInt(KEY_LAST_SIM_COUNT, simCount)
                    .apply()
            } else {
                val isThrottled = (now - lastAlertTime) < ALERT_THROTTLE_MS

                // A. SIM REMOVAL ALERT
                if (lastSimCount > 0 && simCount == 0 && currentSimState == "ABSENT") {
                    if (!isThrottled) {
                        val alert = SecurityAlert(
                            type = "SIM_REMOVED",
                            title = "SIM Card Removed Alert 🚨",
                            message = "Child ke phone se SIM card nikal diya gaya hai! Pichla Carrier: ${lastCarrier.ifEmpty { "Detected SIM" }}. Internet/Cellular network disconnect ho sakta hai.",
                            timestamp = now,
                            severity = "CRITICAL"
                        )
                        FirebaseRepository.pushSecurityAlert(effectiveChildId, alert)
                        prefs.edit().putLong(KEY_LAST_ALERT_TIME, now).apply()
                    }

                    prefs.edit()
                        .putString(KEY_LAST_FINGERPRINT, currentFingerprint)
                        .putString(KEY_LAST_CARRIER, "No SIM")
                        .putInt(KEY_LAST_SIM_COUNT, 0)
                        .apply()
                }
                // B. SIM CHANGED / SWAPPED ALERT
                else if (currentFingerprint != lastFingerprint && simCount > 0 && currentCarrier.isNotEmpty()) {
                    // Normalize and verify if carrier is actually distinct
                    val isCarrierSame = lastCarrier.isNotEmpty() && (
                        lastCarrier.equals(currentCarrier, ignoreCase = true) ||
                        lastCarrier.contains(currentCarrier, ignoreCase = true) ||
                        currentCarrier.contains(lastCarrier, ignoreCase = true)
                    )

                    if (!isCarrierSame && !isThrottled) {
                        val alert = SecurityAlert(
                            type = "SIM_CHANGED",
                            title = "SIM Card Changed Alert 🚨",
                            message = "Child ke phone me naya SIM card lagaya gaya hai! Naya Carrier: $currentCarrier ($currentCountry). Pichla Carrier: ${lastCarrier.ifEmpty { "Unknown" }}.",
                            timestamp = now,
                            severity = "CRITICAL"
                        )
                        FirebaseRepository.pushSecurityAlert(effectiveChildId, alert)
                        prefs.edit().putLong(KEY_LAST_ALERT_TIME, now).apply()
                    }

                    prefs.edit()
                        .putString(KEY_LAST_FINGERPRINT, currentFingerprint)
                        .putString(KEY_LAST_CARRIER, currentCarrier)
                        .putInt(KEY_LAST_SIM_COUNT, simCount)
                        .apply()
                }
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[SimChangeDetector] error: ${e.localizedMessage}")
        }
    }
}
