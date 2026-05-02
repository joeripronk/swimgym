package com.swimgym.app.di

import android.content.Context
import androidx.room.Room
import com.swimgym.app.data.api.WebScraper
import com.swimgym.app.data.local.SwimGymDatabase
import com.swimgym.app.data.repository.AuthRepositoryImpl
import com.swimgym.app.data.repository.CalendarRepositoryImpl
import com.swimgym.app.data.repository.SessionRepository
import com.swimgym.app.data.repository.TrainingRepositoryImpl
import com.swimgym.app.domain.model.User
import com.swimgym.app.domain.repository.AuthRepository
import com.swimgym.app.domain.repository.CalendarRepository
import com.swimgym.app.domain.repository.TrainingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindTrainingRepository(impl: TrainingRepositoryImpl): TrainingRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideSessionRepository(@ApplicationContext context: Context): SessionRepository {
        return SessionRepository(context)
    }

    @Provides
    @Singleton
    fun provideUserFlow(): MutableStateFlow<User?> {
        return MutableStateFlow(null)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SwimGymDatabase {
        return Room.databaseBuilder(
            context,
            SwimGymDatabase::class.java,
            "swimgym.db"
        ).fallbackToDestructiveMigration()
         .allowMainThreadQueries()
         .build()
    }

    @Provides
    fun provideDao(database: SwimGymDatabase) = database.dao()
}