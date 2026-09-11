package com.example.authapp.service

import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.authapp.data.FirebaseRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebaseRepository.updateFcmToken()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val action = data["action"]
        val streamType = data["streamType"] ?: "audio"
        val sessionId = data["sessionId"] ?: ""

        if (action == "START_STREAM") {
            val intent = Intent(this, ChildForegroundService::class.java).apply {
                this.action = ChildForegroundService.ACTION_START
                putExtra(ChildForegroundService.EXTRA_STREAM_TYPE, streamType)
                putExtra(ChildForegroundService.EXTRA_SESSION_ID, sessionId)
            }
            ContextCompat.startForegroundService(this, intent)
        } else if (action == "STOP_STREAM") {
            val intent = Intent(this, ChildForegroundService::class.java).apply {
                this.action = ChildForegroundService.ACTION_STOP
            }
            startService(intent)
        }
    }
}
