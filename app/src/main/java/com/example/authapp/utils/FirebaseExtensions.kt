package com.example.authapp.utils

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Extension helpers to eliminate repetitive ValueEventListener boilerplate code (Point 1).
 */
inline fun Query.onValueChange(
    crossinline onError: (DatabaseError) -> Unit = {},
    crossinline onData: (DataSnapshot) -> Unit
): ValueEventListener {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            onData(snapshot)
        }

        override fun onCancelled(error: DatabaseError) {
            onError(error)
        }
    }
    addValueEventListener(listener)
    return listener
}

inline fun Query.onSingleValue(
    crossinline onError: (DatabaseError) -> Unit = {},
    crossinline onData: (DataSnapshot) -> Unit
) {
    addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            onData(snapshot)
        }

        override fun onCancelled(error: DatabaseError) {
            onError(error)
        }
    })
}

/**
 * Extension helpers to convert DataSnapshot into typed List objects cleanly (Point 4).
 */
inline fun <reified T> DataSnapshot.toListOf(): List<T> {
    val list = mutableListOf<T>()
    for (child in children) {
        val item = child.getValue(T::class.java)
        if (item != null) {
            list.add(item)
        }
    }
    return list
}
