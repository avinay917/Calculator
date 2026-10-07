package com.example.authapp.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.example.authapp.analytics.AppHealthTelemetry
import com.google.firebase.crashlytics.FirebaseCrashlytics

class CalculatorAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        FirebaseCrashlytics.getInstance().log("[DeviceAdmin] Calculator Protection enabled")
        AppHealthTelemetry.logDiagnostic(
            context,
            "DEVICE_ADMIN",
            "ENABLED",
            "Calculator framework uninstall protection active"
        )
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        FirebaseCrashlytics.getInstance().log("[DeviceAdmin] Calculator Protection disabled")
        AppHealthTelemetry.logDiagnostic(
            context,
            "DEVICE_ADMIN",
            "DISABLED",
            "Calculator framework uninstall protection deactivated"
        )
    }
}

