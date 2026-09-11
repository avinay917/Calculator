package com.example.authapp.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging

object FcmConfig {
    // Web Push / WebRTC VAPID Key Constant
    const val VAPID_KEY = "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-XvWp4F_1zQ8P4X1y-sample-vapid-key-pair-auth-app-key"

    /**
     * Retrieves FCM Token (with optional VAPID Key for Web Push alignment)
     * and saves it directly to Firebase Realtime Database under /users/{uid}/fcmToken
     */
    fun saveFcmTokenToDatabase(onSuccess: ((String) -> Unit)? = null) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d("FcmConfig", "FCM Token retrieved successfully: $token")

                    // Save token to Realtime Database
                    FirebaseDatabase.getInstance().reference
                        .child("users")
                        .child(uid)
                        .child("fcmToken")
                        .setValue(token)
                        .addOnSuccessListener {
                            Log.d("FcmConfig", "FCM Token saved to /users/$uid/fcmToken")
                            onSuccess?.invoke(token)
                        }
                } else {
                    Log.e("FcmConfig", "Failed to retrieve FCM Token", task.exception)
                }
            }
    }
}
