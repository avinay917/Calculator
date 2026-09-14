package com.example.authapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.authapp.data.local.entity.CachedUserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: CachedUserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<CachedUserEntity>)

    @Query("SELECT * FROM cached_users WHERE uid = :uid")
    suspend fun getUserById(uid: String): CachedUserEntity?

    @Query("SELECT * FROM cached_users ORDER BY timestamp DESC")
    fun getAllUsers(): Flow<List<CachedUserEntity>>

    @Query("DELETE FROM cached_users WHERE timestamp < :cutoffTime")
    suspend fun deleteOldUsers(cutoffTime: Long)

    @Query("DELETE FROM cached_users")
    suspend fun deleteAll()
}
