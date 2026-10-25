package com.example.authapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.authapp.security.SimChangeDetector

class SimChangeReceiver : BroadcastReceiver() {

    companion object {
        fun checkAndSyncSimState(context: Context) {
            SimChangeDetector.checkSimChange(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        SimChangeDetector.checkSimChange(context)
    }
}
