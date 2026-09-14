package com.example.authapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.authapp.data.local.entity.CachedRecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: CachedRecordingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordings(recordings: List<CachedRecordingEntity>)

    @Query("SELECT * FROM cached_recordings WHERE childId = :childId ORDER BY timestamp DESC")
    fun getRecordingsByChild(childId: String): Flow<List<CachedRecordingEntity>>

    @Query("SELECT * FROM cached_recordings WHERE streamType = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecordingsByType(type: String, limit: Int = 50): List<CachedRecordingEntity>

    @Query("DELETE FROM cached_recordings WHERE timestamp < :cutoffTime")
    suspend fun deleteOldRecordings(cutoffTime: Long)

    @Query("DELETE FROM cached_recordings")
    suspend fun deleteAll()
}
