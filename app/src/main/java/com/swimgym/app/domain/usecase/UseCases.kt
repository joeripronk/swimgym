package com.swimgym.app.domain.usecase

import android.content.Context
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.domain.model.*
import com.swimgym.app.domain.repository.AuthRepository
import com.swimgym.app.domain.repository.SwodLevel
import com.swimgym.app.domain.repository.SyncStatus
import com.swimgym.app.domain.repository.TrainingRepository
import kotlinx.coroutines.flow.Flow
/*
class LoginUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        if (email.isBlank()) return Result.failure(Exception("Email required"))
        if (password.isBlank()) return Result.failure(Exception("Password required"))
        return authRepository.login(email, password)
    }
}
*/
class LogoutUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> = authRepository.logout()
}

class GetScheduleUseCase(
    private val trainingRepository: TrainingRepository
) {
    suspend operator fun invoke(level: SwodLevel = SwodLevel.ALL, hideFullyBooked: Boolean = true, startDate: String? = null): Result<List<Training>> = 
        trainingRepository.getSchedule(level, hideFullyBooked, startDate)
}

class GetTrainingDetailsUseCase(
    private val trainingRepository: TrainingRepository
) {
    suspend operator fun invoke(trainingId: String): Result<Training> =
        trainingRepository.getTrainingDetails(trainingId)
}

class BookTrainingUseCase(
    private val trainingRepository: TrainingRepository
) {
    suspend operator fun invoke(training: TrainingEntity,context: Context): Result<Booking> {
        return trainingRepository.bookTraining(training,context)
    }
}

class CancelBookingUseCase(
    private val trainingRepository: TrainingRepository
) {
    suspend operator fun invoke(training: TrainingEntity,context: Context): Result<Booking> {

        return trainingRepository.cancelBooking(training,context)
    }
}

class GetMyBookingsUseCase(
    private val trainingRepository: TrainingRepository
) {
    operator fun invoke(): Flow<List<Booking>> = trainingRepository.getMyBookings()
}

class IsLoggedInUseCase(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<Boolean> = authRepository.isLoggedIn()
}

class RefreshScheduleUseCase(
    private val trainingRepository: TrainingRepository
) {
    suspend operator fun invoke(): Result<Unit> = trainingRepository.refreshSchedule()
}

class GetSyncStatusUseCase(
    private val trainingRepository: TrainingRepository
) {
    operator fun invoke(): Flow<SyncStatus> = trainingRepository.getSyncStatus()
}