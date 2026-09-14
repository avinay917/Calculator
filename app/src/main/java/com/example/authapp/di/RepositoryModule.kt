package com.example.authapp.di

import android.content.Context
import com.example.authapp.data.FirebaseRepository
import com.example.authapp.security.EncryptionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Singleton
    @Provides
    fun provideFirebaseRepository(): FirebaseRepository {
        return FirebaseRepository
    }

    @Singleton
    @Provides
    fun provideEncryptionManager(@ApplicationContext context: Context): EncryptionManager {
        return EncryptionManager(context)
    }
}
