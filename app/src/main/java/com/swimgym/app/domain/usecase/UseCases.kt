package com.swimgym.app.domain.usecase

import com.swimgym.app.domain.model.*
import com.swimgym.app.domain.repository.AuthRepository
import com.swimgym.app.domain.repository.SwodLevel
import com.swimgym.app.domain.repository.SyncStatus
import com.swimgym.app.domain.repository.TrainingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        if (email.isBlank()) return Result.failure(Exception("Email required"))
        if (password.isBlank()) return Result.failure(Exception("Password required"))
        return authRepository.login(email, password)
    }
}

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> = authRepository.logout()
}

class GetScheduleUseCase @Inject constructor(
    private val trainingRepository: TrainingRepository
) {
    suspend operator fun invoke(level: SwodLevel = SwodLevel.ALL, hideFullyBooked: Boolean = true, startDate: String? = null): Result<List<Training>> = 
        trainingRepository.getSchedule(level, hideFullyBooked, startDate)
}

class GetTrainingDetailsUseCase @Inject constructor(
    private val trainingRepository: TrainingRepository
) {
    suspend operator fun invoke(trainingId: String): Result<Training> =
        trainingRepository.getTrainingDetails(trainingId)
}

class BookTrainingUseCase @Inject constructor(
    private val trainingRepository: TrainingRepository
) {
    suspend operator fun invoke(trainingId: String, className: String = "", classTime: String = "", classDate: String = ""): Result<Booking> {
        return trainingRepository.bookTraining(trainingId, className, classTime, classDate)
    }
}

class CancelBookingUseCase @Inject constructor(
    private val trainingRepository: TrainingRepository
) {
    suspend operator fun invoke(trainingId: String, className: String = "", classTime: String = "", classDate: String = ""): Result<Booking> {
        return trainingRepository.cancelBooking(trainingId, className, classTime, classDate)
    }
}

class GetMyBookingsUseCase @Inject constructor(
    private val trainingRepository: TrainingRepository
) {
    operator fun invoke(): Flow<List<Booking>> = trainingRepository.getMyBookings()
}

class IsLoggedInUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<Boolean> = authRepository.isLoggedIn()
}

class RefreshScheduleUseCase @Inject constructor(
    private val trainingRepository: TrainingRepository
) {
    suspend operator fun invoke(): Result<Unit> = trainingRepository.refreshSchedule()
}

class GetSyncStatusUseCase @Inject constructor(
    private val trainingRepository: TrainingRepository
) {
    operator fun invoke(): Flow<SyncStatus> = trainingRepository.getSyncStatus()
}