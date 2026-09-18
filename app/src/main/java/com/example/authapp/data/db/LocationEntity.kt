package com.example.authapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val timestamp: Long,
    val provider: String
)
