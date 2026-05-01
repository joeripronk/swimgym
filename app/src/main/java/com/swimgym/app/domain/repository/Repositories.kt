package com.swimgym.app.domain.repository

import com.swimgym.app.domain.model.*
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
    suspend fun getSchedule(level: SwodLevel = SwodLevel.ALL, hideFullyBooked: Boolean = false, startDate: String? = null): Result<List<Training>>
    suspend fun getTrainingDetails(trainingId: String): Result<Training>
    suspend fun bookTraining(trainingId: String, className: String = "", classTime: String = "", classDate: String = ""): Result<Booking>
    suspend fun cancelBooking(trainingId: String, className: String = "", classTime: String = "", classDate: String = ""): Result<Booking>
    fun getMyBookings(): Flow<List<Booking>>
    suspend fun refreshSchedule(): Result<Unit>
    fun getSyncStatus(): Flow<SyncStatus>
}

data class SyncStatus(
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = 0L,
    val nextSyncTime: Long = 0L,
    val isDataStale: Boolean = false
)