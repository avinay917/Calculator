package com.example.authapp.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object FirebaseUserRepository {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val database: FirebaseDatabase get() = FirebaseDatabase.getInstance()

    fun listenToUserRole(uid: String, onRoleChanged: (String) -> Unit) {
        database.reference.child("users").child(uid).child("role")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val role = snapshot.getValue(String::class.java) ?: "child"
                    onRoleChanged(role)
                }

                override fun onCancelled(error: DatabaseError) {
                    onRoleChanged("child")
                }
            })
    }

    fun getUserRoleOnce(uid: String, onRoleFetched: (String) -> Unit) {
        database.reference.child("users").child(uid).child("role")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val role = snapshot.getValue(String::class.java) ?: "child"
                    onRoleFetched(role)
                }

                override fun onCancelled(error: DatabaseError) {
                    onRoleFetched("child")
                }
            })
    }

    fun saveCachedChildUsers(context: Context, list: List<User>) {
        try {
            val json = com.google.gson.Gson().toJson(list)
            val prefs = context.getSharedPreferences("parent_cache_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("cached_child_users", json).apply()
        } catch (_: Exception) {}
    }

    fun getCachedChildUsers(context: Context): List<User> {
        return try {
            val prefs = context.getSharedPreferences("parent_cache_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("cached_child_users", null) ?: return emptyList()
            val type = object : com.google.gson.reflect.TypeToken<List<User>>() {}.type
            com.google.gson.Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun listenToChildUsers(onUsersUpdated: (List<User>) -> Unit): ValueEventListener {
        val parentUid = auth.currentUser?.uid
        if (parentUid.isNullOrEmpty()) {
            return object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) = onUsersUpdated(emptyList())
                override fun onCancelled(error: DatabaseError) {
                    FirebaseRepository.recordNonFatalError("listenToChildUsers: not authenticated", error.toException())
                }
            }
        }
        val ref = database.reference.child("users")
            .orderByChild("parentId")
            .equalTo(parentUid)
        try { ref.keepSynced(true) } catch (_: Exception) {}
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentParentUid = auth.currentUser?.uid ?: ""
                val list = mutableListOf<User>()
                for (child in snapshot.children) {
                    try {
                        val user = child.getValue(User::class.java)
                        if (user != null && user.role == "child" && user.parentId == currentParentUid) {
                            val isOnlineVal = child.child("isOnline").getValue(Boolean::class.java) ?: user.isOnline
                            val lastSeenVal = child.child("lastSeen").getValue(Long::class.java) ?: user.lastSeen
                            list.add(user.copy(isOnline = isOnlineVal, lastSeen = lastSeenVal))
                        }
                    } catch (e: Exception) {
                        FirebaseRepository.recordNonFatalError("listenToChildUsers: Skipping corrupt child node [${child.key}]: ${e.message}", e)
                    }
                }
                onUsersUpdated(list)
            }

            override fun onCancelled(error: DatabaseError) {
                FirebaseRepository.recordNonFatalError("listenToChildUsers cancelled: ${error.message}", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun linkChildToParent(childId: String, parentId: String, onComplete: (Boolean, String?) -> Unit) {
        val updates = mapOf<String, Any>("parentId" to parentId)
        database.reference.child("users").child(childId).updateChildren(updates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(true, null)
                } else {
                    onComplete(false, task.exception?.localizedMessage)
                }
            }
    }
}
