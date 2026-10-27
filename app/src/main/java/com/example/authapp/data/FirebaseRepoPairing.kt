package com.example.authapp.data

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object FirebasePairingRepository {
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val crashlytics: FirebaseCrashlytics get() = FirebaseCrashlytics.getInstance()

    private fun getOrGeneratePairingCodeInternal(
        uid: String,
        email: String,
        nodePath: String,
        userCodeField: String,
        uidField: String,
        emailField: String,
        onComplete: (code: String?, error: String?) -> Unit
    ) {
        if (uid.isBlank()) {
            onComplete(null, "UID is empty")
            return
        }
        val userRef = database.reference.child("users").child(uid)
        userRef.child(userCodeField).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val existingCode = snapshot.getValue(String::class.java)
                if (!existingCode.isNullOrBlank()) {
                    database.reference.child(nodePath).child(existingCode).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(codeSnap: DataSnapshot) {
                            val isUsed = codeSnap.child("isUsed").getValue(Boolean::class.java) ?: false
                            if (codeSnap.exists() && !isUsed) {
                                onComplete(existingCode, null)
                            } else {
                                createNewPairingCodeInternal(uid, email, nodePath, userCodeField, uidField, emailField, onComplete)
                            }
                        }
                        override fun onCancelled(err: DatabaseError) {
                            createNewPairingCodeInternal(uid, email, nodePath, userCodeField, uidField, emailField, onComplete)
                        }
                    })
                } else {
                    createNewPairingCodeInternal(uid, email, nodePath, userCodeField, uidField, emailField, onComplete)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                createNewPairingCodeInternal(uid, email, nodePath, userCodeField, uidField, emailField, onComplete)
            }
        })
    }

    private fun createNewPairingCodeInternal(
        uid: String,
        email: String,
        nodePath: String,
        userCodeField: String,
        uidField: String,
        emailField: String,
        onComplete: (code: String?, error: String?) -> Unit
    ) {
        val random6Digit = (100000..999999).random().toString()
        val codeData = mapOf<String, Any>(
            uidField to uid,
            emailField to email,
            "createdAt" to ServerValue.TIMESTAMP,
            "isUsed" to false
        )
        database.reference.child(nodePath).child(random6Digit).setValue(codeData)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    database.reference.child("users").child(uid).child(userCodeField).setValue(random6Digit)
                    onComplete(random6Digit, null)
                } else {
                    onComplete(null, task.exception?.localizedMessage ?: "Failed to generate pairing code")
                }
            }
    }

    fun getOrGenerateParentPairingCode(parentUid: String, parentEmail: String, onComplete: (code: String?, error: String?) -> Unit) =
        getOrGeneratePairingCodeInternal(parentUid, parentEmail, "pairing_codes", "pairingCode", "parentUid", "parentEmail", onComplete)

    fun generateNewParentPairingCode(parentUid: String, parentEmail: String, onComplete: (code: String?, error: String?) -> Unit) =
        createNewPairingCodeInternal(parentUid, parentEmail, "pairing_codes", "pairingCode", "parentUid", "parentEmail", onComplete)

    fun pairChildWithCode(childUid: String, code: String, onResult: (success: Boolean, parentEmail: String?, errorMsg: String?) -> Unit) {
        val trimmedCode = code.trim()
        if (childUid.isBlank()) {
            onResult(false, null, "Child UID is missing")
            return
        }
        if (trimmedCode.length != 6 || !trimmedCode.all(Char::isDigit)) {
            onResult(false, null, "Please enter a valid 6-digit code")
            return
        }

        val codeRef = database.reference.child("pairing_codes").child(trimmedCode)
        codeRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): Transaction.Result {
                if (currentData.value == null) return Transaction.abort()

                val isUsed = currentData.child("isUsed").getValue(Boolean::class.java) ?: false
                val createdAt = currentData.child("createdAt").getValue(Long::class.java) ?: 0L
                val parentUid = currentData.child("parentUid").getValue(String::class.java)

                if (isUsed || createdAt <= 0L || System.currentTimeMillis() - createdAt > 10 * 60 * 1000L || parentUid.isNullOrBlank()) {
                    return Transaction.abort()
                }

                currentData.child("linkedChildUid").value = childUid
                currentData.child("isUsed").value = true
                currentData.child("linkedAt").value = ServerValue.TIMESTAMP
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    onResult(false, null, error.message)
                    return
                }
                if (!committed || currentData == null) {
                    onResult(false, null, "Pairing code is invalid, expired, or already used. Please generate a new code.")
                    return
                }

                val parentUid = currentData.child("parentUid").getValue(String::class.java)
                val parentEmail = currentData.child("parentEmail").getValue(String::class.java) ?: "Parent"
                if (parentUid.isNullOrBlank()) {
                    onResult(false, null, "Pairing code data corrupted")
                    return
                }

                database.reference.child("users").child(childUid).child("parentId").setValue(parentUid)
                    .addOnCompleteListener { updateTask ->
                        if (updateTask.isSuccessful) {
                            firestore.collection("users").document(childUid)
                                .set(mapOf("parentId" to parentUid), SetOptions.merge())
                                .addOnFailureListener { e -> crashlytics.recordException(e) }
                            onResult(true, parentEmail, null)
                        } else {
                            codeRef.runTransaction(object : Transaction.Handler {
                                override fun doTransaction(data: com.google.firebase.database.MutableData): Transaction.Result {
                                    if (data.child("linkedChildUid").getValue(String::class.java) == childUid &&
                                        data.child("isUsed").getValue(Boolean::class.java) == true) {
                                        data.child("linkedChildUid").value = null
                                        data.child("isUsed").value = false
                                        data.child("linkedAt").value = null
                                    }
                                    return Transaction.success(data)
                                }
                                override fun onComplete(
                                    error: DatabaseError?,
                                    committed: Boolean,
                                    snapshot: DataSnapshot?
                                ) = Unit
                            })
                            onResult(false, null, updateTask.exception?.localizedMessage ?: "Failed to link to parent")
                        }
                    }
            }
        })
    }

    fun listenToPairingCodeStatus(code: String, onLinked: (childUid: String) -> Unit): ValueEventListener {
        val ref = database.reference.child("pairing_codes").child(code).child("linkedChildUid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val childUid = snapshot.getValue(String::class.java)
                if (!childUid.isNullOrEmpty()) {
                    onLinked(childUid)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun listenToChildParentLink(childUid: String, onParentIdUpdated: (String) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val parentId = snapshot.getValue(String::class.java) ?: ""
                onParentIdUpdated(parentId)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        database.reference.child("users").child(childUid).child("parentId").addValueEventListener(listener)
        return listener
    }

    fun getOrGenerateChildPairingCode(childUid: String, childEmail: String, onComplete: (code: String?, error: String?) -> Unit) =
        getOrGeneratePairingCodeInternal(childUid, childEmail, "child_pairing_codes", "childPairingCode", "childUid", "childEmail", onComplete)

    fun generateNewChildPairingCode(childUid: String, childEmail: String, onComplete: (code: String?, error: String?) -> Unit) =
        createNewPairingCodeInternal(childUid, childEmail, "child_pairing_codes", "childPairingCode", "childUid", "childEmail", onComplete)

    fun pairParentWithChildCode(
        parentUid: String,
        parentEmail: String,
        code: String,
        onResult: (success: Boolean, childUid: String?, childEmail: String?, errorMsg: String?) -> Unit
    ) {
        val trimmedCode = code.trim()
        if (parentUid.isBlank()) {
            onResult(false, null, null, "Parent UID is missing")
            return
        }
        if (trimmedCode.length != 6 || !trimmedCode.all(Char::isDigit)) {
            onResult(false, null, null, "Please enter a valid 6-digit code")
            return
        }

        val codeRef = database.reference.child("child_pairing_codes").child(trimmedCode)
        codeRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: com.google.firebase.database.MutableData): Transaction.Result {
                if (currentData.value == null) return Transaction.abort()

                val isUsed = currentData.child("isUsed").getValue(Boolean::class.java) ?: false
                val childUid = currentData.child("childUid").getValue(String::class.java)
                if (isUsed || childUid.isNullOrBlank()) {
                    return Transaction.abort()
                }

                currentData.child("linkedParentUid").value = parentUid
                currentData.child("linkedParentEmail").value = parentEmail
                currentData.child("isUsed").value = true
                currentData.child("linkedAt").value = ServerValue.TIMESTAMP
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    onResult(false, null, null, error.message)
                    return
                }
                if (!committed || currentData == null) {
                    onResult(false, null, null, "Pairing code is invalid, already used, or expired.")
                    return
                }

                val childUid = currentData.child("childUid").getValue(String::class.java)
                val childEmail = currentData.child("childEmail").getValue(String::class.java) ?: "Child Device"
                if (childUid.isNullOrBlank()) {
                    onResult(false, null, null, "Child pairing data corrupted")
                    return
                }

                database.reference.child("users").child(childUid).child("parentId").setValue(parentUid)
                    .addOnCompleteListener { updateTask ->
                        if (updateTask.isSuccessful) {
                            try {
                                firestore.collection("users").document(childUid)
                                    .set(mapOf("parentId" to parentUid), SetOptions.merge())
                            } catch (_: Exception) {}
                            onResult(true, childUid, childEmail, null)
                        } else {
                            onResult(false, null, null, updateTask.exception?.localizedMessage ?: "Failed to link device")
                        }
                    }
            }
        })
    }

    fun unpairChild(childUid: String, onComplete: (Boolean, String?) -> Unit) {
        val updates = mapOf<String, Any?>(
            "users/$childUid/parentId" to null,
            "users/$childUid/childPairingCode" to null,
            "streams/$childUid" to null
        )
        database.reference.updateChildren(updates).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                try {
                    firestore.collection("users").document(childUid)
                        .set(mapOf("parentId" to ""), SetOptions.merge())
                } catch (_: Exception) {}
                onComplete(true, null)
            } else {
                onComplete(false, task.exception?.localizedMessage ?: "Failed to unpair child")
            }
        }
    }
}
