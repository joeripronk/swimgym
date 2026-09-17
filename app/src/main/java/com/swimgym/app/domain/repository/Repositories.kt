package com.swimgym.app.domain.repository

import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
    suspend fun logout(): Result<Unit>
    fun isLoggedIn(): Flow<Boolean>
    fun getCurrentUser(): Flow<User?>
}

enum class SwodLevel {
    ALL, STARTERS, MID, PRO
}

interface TrainingRepository {
    suspend fun getSchedule(level: SwodLevel = SwodLevel.ALL, hideFullyBooked: Boolean = false, startDate: String? = null): Result<List<com.swimgym.app.domain.model.Training>>
    suspend fun getTrainingDetails(trainingId: String): Result<com.swimgym.app.domain.model.Training>
    suspend fun bookTraining(training: TrainingEntity): Result<com.swimgym.app.domain.model.Booking>
    suspend fun cancelBooking(training: TrainingEntity): Result<com.swimgym.app.domain.model.Booking>
    fun getMyBookings(): Flow<List<com.swimgym.app.domain.model.Booking>>
    suspend fun refreshSchedule(): Result<Unit>
    fun getSyncStatus(): Flow<SyncStatus>
}

data class SyncStatus(
    val isSyncing: Boolean = false
)