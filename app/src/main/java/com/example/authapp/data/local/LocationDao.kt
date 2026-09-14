package com.example.authapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.authapp.data.local.entity.CachedLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: CachedLocationEntity)

    @Query("SELECT * FROM cached_locations WHERE childId = :childId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestLocation(childId: String): Flow<CachedLocationEntity?>

    @Query("SELECT * FROM cached_locations WHERE childId = :childId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLocationHistory(childId: String, limit: Int = 100): List<CachedLocationEntity>

    @Query("DELETE FROM cached_locations WHERE timestamp < :cutoffTime")
    suspend fun deleteOldLocations(cutoffTime: Long)

    @Query("DELETE FROM cached_locations")
    suspend fun deleteAll()
}
