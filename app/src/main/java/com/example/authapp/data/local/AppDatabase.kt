package com.example.authapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.authapp.data.local.entity.CachedRecordingEntity
import com.example.authapp.data.local.entity.CachedUserEntity
import com.example.authapp.data.local.entity.CachedLocationEntity

/**
 * Offline-first Room database for caching.
 * Enables app to work without internet connection.
 */
@Database(
    entities = [
        CachedUserEntity::class,
        CachedRecordingEntity::class,
        CachedLocationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun recordingDao(): RecordingDao
    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
