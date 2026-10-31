package com.example.authapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging

object FirebaseAuthRepository {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()
    private val firestore: com.google.firebase.firestore.FirebaseFirestore get() = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    private val crashlytics: com.google.firebase.crashlytics.FirebaseCrashlytics get() = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()

    fun syncUserProfileToFirestore(uid: String) {
        if (uid.isEmpty()) return
        database.reference.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    firestore.collection("users").document(uid).set(user, com.google.firebase.firestore.SetOptions.merge())
                        .addOnFailureListener { e -> crashlytics.recordException(e) }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun signUp(
        fullName: String,
        email: String,
        password: String,
        role: String = "child",
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = task.result?.user
                    val uid = firebaseUser?.uid ?: ""
                    val user = User(
                        uid = uid,
                        name = fullName,
                        email = email,
                        role = if (role.lowercase() == "parent") "parent" else "child",
                        isOnline = false
                    )
                    database.reference.child("users").child(uid).setValue(user)
                        .addOnCompleteListener { dbTask ->
                            if (dbTask.isSuccessful) {
                                // Sync user profile document to Firestore as well to satisfy security rules
                                firestore.collection("users").document(uid).set(user)
                                    .addOnFailureListener { e -> crashlytics.recordException(e) }
                                // Send verification email link before signing out
                                firebaseUser?.sendEmailVerification()
                                auth.signOut()
                                onResult(true, null)
                            } else {
                                auth.currentUser?.delete()
                                onResult(false, dbTask.exception?.localizedMessage ?: "Failed to save user profile")
                            }
                        }
                } else {
                    onResult(false, task.exception?.localizedMessage)
                }
            }
    }

    fun signIn(
        email: String,
        password: String,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && !user.isEmailVerified) {
                        // User has not verified their email - reject login and send verification link
                        try {
                            user.sendEmailVerification()
                        } catch (_: Exception) {}
                        auth.signOut()
                        onResult(false, "Aapka email verify nahi hai. Kripya apne email inbox mein bheje gaye link par click karke pehle verify karein.")
                        return@addOnCompleteListener
                    }
                    val uid = user?.uid
                    if (uid != null) {
                        FirebaseRepository.setupPresenceSystem(uid)
                        syncUserProfileToFirestore(uid)
                    }
                    updateFcmToken()
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.localizedMessage)
                }
            }
    }

    fun sendPasswordResetEmail(
        email: String,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            onResult(false, "Please enter your email address")
            return
        }
        auth.sendPasswordResetEmail(trimmedEmail)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.localizedMessage ?: "Failed to send reset email")
                }
            }
    }

    fun signOut() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            val userRef = database.reference.child("users").child(uid)
            val offlineMap = mapOf<String, Any>(
                "isOnline" to false,
                "online" to false,
                "lastSeen" to ServerValue.TIMESTAMP
            )
            userRef.updateChildren(offlineMap)
            userRef.onDisconnect().cancel()
        }
        FirebaseRepository.clearPresenceListener()
        auth.signOut()
    }

    fun setOnlineStatus(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = database.reference.child("users").child(uid)
        val statusMap = mapOf<String, Any>(
            "isOnline" to isOnline,
            "online" to isOnline,
            "lastSeen" to ServerValue.TIMESTAMP
        )
        userRef.updateChildren(statusMap)
        if (isOnline) {
            FirebaseRepository.setupPresenceSystem(uid)
            syncUserProfileToFirestore(uid)
        }
    }

    private var presenceConnectedListener: ValueEventListener? = null
    private var currentPresenceUid: String? = null

    fun setupPresenceSystem(uid: String) {
        if (currentPresenceUid == uid && presenceConnectedListener != null) return
        clearPresenceListener()
        currentPresenceUid = uid

        val userRef = database.reference.child("users").child(uid)
        val connectedRef = database.reference.child(".info/connected")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    val disconnectMap = mapOf<String, Any>(
                        "isOnline" to false,
                        "online" to false,
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                    userRef.onDisconnect().updateChildren(disconnectMap)

                    val onlineMap = mapOf<String, Any>(
                        "isOnline" to true,
                        "online" to true,
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                    userRef.updateChildren(onlineMap)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                FirebaseRepository.recordNonFatalError("Presence listener cancelled: ${error.message}", error.toException())
            }
        }

        connectedRef.addValueEventListener(listener)
        presenceConnectedListener = listener
    }

    fun clearPresenceListener() {
        val listener = presenceConnectedListener
        if (listener != null) {
            database.reference.child(".info/connected").removeEventListener(listener)
            presenceConnectedListener = null
        }
        currentPresenceUid = null
    }

    fun updateFcmToken() {
        val uid = auth.currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                database.reference.child("users").child(uid).child("fcmToken").setValue(token)
            }
        }
    }
}
