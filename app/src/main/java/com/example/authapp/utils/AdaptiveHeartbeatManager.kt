package com.example.authapp.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.authapp.analytics.AppHealthTelemetry
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Adaptive Heartbeat & Stationary Device Battery Saver
 *
 * - Active state (Screen ON ya streaming): Heartbeat interval = 60s (1 min).
 * - Stationary state (Screen OFF aur non-streaming): Heartbeat interval = 300s (5 min).
 * - Motion Trigger: Hardware-level Significant Motion Sensor (DSP) detect karte hi ya Screen ON hote hi
 *   turant heartbeat fire hoti hai aur active interval restore hota hai.
 * - Result: Background CPU wakeups 70-80% kam hote hain, battery life significantly improve hoti hai.
 */
class AdaptiveHeartbeatManager(
    private val context: Context,
    private val stateProvider: () -> String
) {
    companion object {
        const val ACTIVE_INTERVAL_MS = 60_000L       // 1 minute when active / streaming / screen ON
        const val STATIONARY_INTERVAL_MS = 300_000L  // 5 minutes when screen OFF & stationary
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isStreaming = false
    private var isScreenOn = true
    private var isReceiverRegistered = false
    private var isSensorArmed = false

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val motionSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    private val triggerEventListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            isSensorArmed = false
            FirebaseCrashlytics.getInstance().log("[AdaptiveHeartbeat] Motion detected by sensor, waking heartbeat")
            triggerImmediateHeartbeat()
            armMotionSensorIfNeeded()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    disarmMotionSensor()
                    triggerImmediateHeartbeat()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    armMotionSensorIfNeeded()
                    reschedule(STATIONARY_INTERVAL_MS)
                }
            }
        }
    }

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            sendCurrentHeartbeat()
            val nextDelay = getAdaptiveInterval()
            handler.postDelayed(this, nextDelay)
        }
    }

    fun start() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenOn = pm?.isInteractive ?: true

        // Register dynamic screen broadcast receiver
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(
                context,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[AdaptiveHeartbeat] Screen receiver registration failed: ${e.message}")
        }

        armMotionSensorIfNeeded()
        // First heartbeat after active delay
        handler.postDelayed(heartbeatRunnable, ACTIVE_INTERVAL_MS)
    }

    fun setStreamingState(streaming: Boolean) {
        this.isStreaming = streaming
        if (streaming) {
            disarmMotionSensor()
            triggerImmediateHeartbeat()
        } else {
            armMotionSensorIfNeeded()
            reschedule(getAdaptiveInterval())
        }
    }

    fun triggerImmediateHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable)
        sendCurrentHeartbeat()
        val nextDelay = getAdaptiveInterval()
        handler.postDelayed(heartbeatRunnable, nextDelay)
    }

    private fun sendCurrentHeartbeat() {
        try {
            val state = stateProvider()
            AppHealthTelemetry.sendHeartbeat(context, state)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[AdaptiveHeartbeat] sendHeartbeat error: ${e.message}")
        }
    }

    private fun getAdaptiveInterval(): Long {
        return if (isStreaming || isScreenOn) {
            ACTIVE_INTERVAL_MS
        } else {
            STATIONARY_INTERVAL_MS
        }
    }

    private fun reschedule(delayMs: Long) {
        handler.removeCallbacks(heartbeatRunnable)
        handler.postDelayed(heartbeatRunnable, delayMs)
    }

    private fun armMotionSensorIfNeeded() {
        if (!isScreenOn && !isStreaming && motionSensor != null && !isSensorArmed) {
            try {
                isSensorArmed = sensorManager?.requestTriggerSensor(triggerEventListener, motionSensor) == true
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[AdaptiveHeartbeat] Motion sensor arming failed: ${e.message}")
            }
        }
    }

    private fun disarmMotionSensor() {
        if (isSensorArmed && motionSensor != null) {
            try {
                sensorManager?.cancelTriggerSensor(triggerEventListener, motionSensor)
                isSensorArmed = false
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(screenReceiver)
                isReceiverRegistered = false
            } catch (_: Exception) {}
        }
        disarmMotionSensor()
    }
}
