package com.swimgym.app.data.repository

import android.content.Context
import com.swimgym.app.data.api.WebScraper
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.BookingEntity
import com.swimgym.app.data.local.entity.InstructorEntity
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.local.entity.UserEntity
import com.swimgym.app.data.model.Mappers.toDomain
import com.swimgym.app.data.model.Mappers.toEntity
import com.swimgym.app.domain.model.*
import com.swimgym.app.domain.repository.AuthRepository
import com.swimgym.app.domain.repository.SwodLevel
import com.swimgym.app.domain.repository.TrainingRepository
import com.swimgym.app.util.BookingNotificationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val webScraper: WebScraper,
    private val sessionRepo: SessionRepository,
    private val userFlow: MutableStateFlow<User?>,
    private val dao: SwimGymDao
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> {
        return try {
            val result = webScraper.login(email, password)
            result.map { userDto ->
                val user = userDto.toDomain()
                sessionRepo.saveSession("", user.id, user.name, user.email)
                userFlow.value = user
                dao.insertUser(user.toEntity())
                user
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            webScraper.logout()
            sessionRepo.clearSession()
            userFlow.value = null
            dao.clearUsers()
            dao.clearBookings()
            dao.clearTrainings()
            Result.success(Unit)
        } catch (e: Exception) {
            sessionRepo.clearSession()
            userFlow.value = null
            dao.clearUsers()
            dao.clearBookings()
            dao.clearTrainings()
            Result.success(Unit)
        }
    }

    override fun isLoggedIn(): Flow<Boolean> = sessionRepo.isLoggedIn

    override fun getCurrentUser(): Flow<User?> = userFlow
}

@Singleton
class TrainingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webScraper: WebScraper,
    private val userFlow: MutableStateFlow<User?>,
    private val dao: SwimGymDao,
    private val notificationManager: BookingNotificationManager,
) : TrainingRepository {
    private val bookingsFlow = MutableStateFlow<List<Booking>>(emptyList())

    override suspend fun getSchedule(level: SwodLevel, hideFullyBooked: Boolean, startDate: String?): Result<List<Training>> {
        return try {
            val webScraperLevel = when (level) {
                SwodLevel.ALL -> WebScraper.SwodLevel.ALL
                SwodLevel.STARTERS -> WebScraper.SwodLevel.STARTERS
                SwodLevel.MID -> WebScraper.SwodLevel.MID
                SwodLevel.PRO -> WebScraper.SwodLevel.PRO
            }
            val result = webScraper.getSchedule(level = webScraperLevel, hideFullyBooked = hideFullyBooked, startDate = startDate)
            result.map { trainings ->
                val domainTrainings = trainings.map { it.toDomain() }
                dao.insertTrainings(trainings.map { it.toEntity() })
                domainTrainings
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrainingDetails(trainingId: String): Result<Training> {
        return try {
            val cached = dao.getTrainingById(trainingId)
            if (cached == null) {
                return Result.failure( Exception("cannot locate training for details"))
            }
            //    return Result.success(cached.toDomain())
            //}
            val result = webScraper.getTrainingDetails(trainingId)
            result.map { details ->
                val training = Training(
                    id = details.id,
                    title = details.title,
                    instructor = details.instructor,
                    instructorLink = details.instructorLink,
                    location = details.location,
                    spotsAvailable = details.spotsAvailable,
                    startTime = cached.startTime,
                    endTime = cached.endTime,
                    isJoined = details.isJoined,
                    imageUrl = details.imageUrl,
                    description = details.description,
                    cost = details.cost,
                    totalSpots = details.totalSpots,
                    cancelPolicy = details.cancelPolicy
                )
                dao.insertTraining(training.toEntity())
                if (details.imageUrl.isNotEmpty()) {
                    dao.insertInstructor(
                        InstructorEntity(
                            instructorName = details.instructor,
                            instructorLink = details.instructorLink,
                            instructorImage = details.imageUrl
                        )
                    )
                }
                training
            }
        } catch (e: Exception) {
            val cached = dao.getTrainingById(trainingId)
            if (cached != null) {
                Result.success(cached.toDomain())
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun bookTraining(trainingId: String, className: String, classTime: String, classDate: String): Result<Booking> {
        return try {
            val result = webScraper.bookTraining(trainingId, className, classTime, classDate)
            result.map { bookingDto ->
                val booking = bookingDto.toDomain()
                dao.insertBooking(booking.toEntity())
                val current = bookingsFlow.value.toMutableList()
                current.add(booking)
                bookingsFlow.value = current
                notificationManager.showBookingConfirmation(
                    trainingName = booking.className,
                    classTime = booking.classTime,
                    classDate = booking.classDate,
                    isScheduledBooking = false
                )
                booking
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelBooking(trainingId: String, className: String, classTime: String, classDate: String): Result<Booking> {
        return try {
            val result = webScraper.cancelBooking(trainingId, className, classTime, classDate)
            result.map { bookingDto ->
                val booking = bookingDto.toDomain()
                dao.deleteBookingByTrainingId(trainingId)
                val current = bookingsFlow.value.toMutableList()
                current.removeAll { it.trainingId == trainingId }
                bookingsFlow.value = current
                booking
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getMyBookings(): Flow<List<Booking>> = bookingsFlow
}