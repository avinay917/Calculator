package com.example.authapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.authapp.security.SimChangeDetector
import com.example.authapp.sync.PendingUploadQueue
import com.example.authapp.sync.SyncScheduler
import com.google.firebase.crashlytics.FirebaseCrashlytics

class ChildSimAndNetworkMonitor(private val context: Context) {

    private var subscriptionsChangedListener: Any? = null
    private var dynamicSimReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun isNetworkConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun startNetworkRestoreSync() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    if (!PendingUploadQueue.isEmpty(context.applicationContext)) {
                        SyncScheduler.scheduleWorker(context.applicationContext)
                    }
                }
            }
            networkCallback = callback
            cm.registerDefaultNetworkCallback(callback)
            FirebaseCrashlytics.getInstance().log("[ChildService] Network restore sync listener registered")
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] Network callback register error: ${e.localizedMessage}")
        }
    }

    fun startSimMonitoring() {
        SimChangeDetector.checkSimChange(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                    override fun onSubscriptionsChanged() {
                        super.onSubscriptionsChanged()
                        SimChangeDetector.checkSimChange(context.applicationContext)
                    }
                }
                subscriptionsChangedListener = listener
                sm?.addOnSubscriptionsChangedListener(listener)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("[ChildService] SubscriptionsChangedListener register error: ${e.localizedMessage}")
            }
        }
        try {
            dynamicSimReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    SimChangeDetector.checkSimChange(context.applicationContext)
                }
            }
            val filter = IntentFilter().apply {
                addAction("android.intent.action.SIM_STATE_CHANGED")
                addAction("android.telephony.action.DEFAULT_SUBSCRIPTION_CHANGED")
                addAction("android.telephony.action.SIM_CARD_STATE_CHANGED")
                addAction("android.telephony.action.SIM_APPLICATION_STATE_CHANGED")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ContextCompat.registerReceiver(context, dynamicSimReceiver!!, filter, ContextCompat.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(dynamicSimReceiver, filter)
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().log("[ChildService] Dynamic SIM Receiver register error: ${e.localizedMessage}")
        }
    }

    fun cleanup() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subscriptionsChangedListener != null) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                sm?.removeOnSubscriptionsChangedListener(subscriptionsChangedListener as SubscriptionManager.OnSubscriptionsChangedListener)
            }
        } catch (_: Exception) {}
        try { dynamicSimReceiver?.let { context.unregisterReceiver(it) } } catch (_: Exception) {}
        try { networkCallback?.let { (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.unregisterNetworkCallback(it) } } catch (_: Exception) {}
    }
}
