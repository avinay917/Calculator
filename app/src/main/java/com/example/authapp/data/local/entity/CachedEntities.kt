package com.example.authapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_users")
data class CachedUserEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val email: String,
    val role: String,
    val isOnline: Boolean,
    val lastSeen: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_recordings")
data class CachedRecordingEntity(
    @PrimaryKey val id: String,
    val childId: String,
    val streamType: String,
    val duration: Long,
    val storageUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_locations")
data class CachedLocationEntity(
    @PrimaryKey val id: String,
    val childId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long = System.currentTimeMillis()
)
