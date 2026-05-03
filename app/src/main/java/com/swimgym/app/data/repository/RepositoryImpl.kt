package com.swimgym.app.data.repository

import android.content.Context
import com.swimgym.app.data.api.WebScraper
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.CacheControlEntity
import com.swimgym.app.data.local.entity.BookingEntity
import com.swimgym.app.data.local.entity.InstructorEntity
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.model.Mappers.toDomain
import com.swimgym.app.data.model.Mappers.toEntity
import com.swimgym.app.domain.model.*
import com.swimgym.app.domain.model.User as DomainUser
import com.swimgym.app.domain.repository.AuthRepository
import com.swimgym.app.domain.repository.SwodLevel
import com.swimgym.app.domain.repository.TrainingRepository
import com.swimgym.app.domain.repository.SyncStatus
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
    private val userFlow: MutableStateFlow<DomainUser?>,
    private val dao: SwimGymDao
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<DomainUser> {
        return try {
            val result = webScraper.login(email, password)
            result.map { userDto ->
                val user = DomainUser(userDto.id, userDto.name, userDto.email)
                sessionRepo.saveSession("", user.id, user.name, user.email)
                userFlow.value = user
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
            sessionRepo.saveCookies(emptyMap())
            userFlow.value = null
            dao.clearBookings()
            dao.clearTrainings()
            dao.clearCacheControl()
            Result.success(Unit)
        } catch (e: Exception) {
            sessionRepo.clearSession()
            sessionRepo.saveCookies(emptyMap())
            userFlow.value = null
            dao.clearBookings()
            dao.clearTrainings()
            dao.clearCacheControl()
            Result.success(Unit)
        }
    }

    override fun isLoggedIn(): Flow<Boolean> = sessionRepo.isLoggedIn

    override fun getCurrentUser(): Flow<DomainUser?> = userFlow
}

@Singleton
class TrainingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webScraper: WebScraper,
    private val userFlow: MutableStateFlow<DomainUser?>,
    private val dao: SwimGymDao,
    private val notificationManager: BookingNotificationManager,
) : TrainingRepository {

    private val bookingsFlow = MutableStateFlow<List<Booking>>(emptyList())
    private val syncStatusFlow = MutableStateFlow(SyncStatus())

    override suspend fun getSchedule(level: SwodLevel, hideFullyBooked: Boolean, startDate: String?): Result<List<Training>> {
        return try {
            var cachedTrainings = dao.getAllTrainingsList()
            val cacheControl = dao.getCacheControl()
            val isDataStale = cacheControl?.let {
                System.currentTimeMillis() > it.lastSyncTime + it.staleAfterMillis
            } ?: true

            //if (false && cachedTrainings.isEmpty() || isDataStale) {
            //    webScraper.getSchedule()
             //   updateSyncStatus()
              //  cachedTrainings = dao.getAllTrainingsList()
            //}

            val filteredTrainings = cachedTrainings
                .map { it.toDomain() }
                .filter {
                    when (level) {
                        SwodLevel.ALL -> true
                        SwodLevel.STARTERS -> it.title.contains("Starters", ignoreCase = true)
                        SwodLevel.MID -> it.title.contains("Mid", ignoreCase = true)
                        SwodLevel.PRO -> it.title.contains("Pro", ignoreCase = true)
                    }
                }
                .filter { if (hideFullyBooked) !it.isFull || it.isJoined else true }

            Result.success(filteredTrainings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrainingDetails(trainingId: String): Result<Training> {
        return try {
            val cached = dao.getTrainingById(trainingId)
            if (cached == null) {
                return Result.failure(Exception("Cannot locate training for details"))
            }

            val instructor = dao.getInstructor(cached.instructor)
            val result = webScraper.getTrainingDetails(trainingId)

            result.map { details ->
                var instructorLink = ""
                var instructorImage = ""

                if (instructor != null) {
                    instructorLink = instructor.instructorLink
                    instructorImage = instructor.instructorImage
                }

                Training(
                    id = details.id,
                    title = details.title,
                    instructor = details.instructor,
                    instructorLink = instructorLink,
                    location = details.location,
                    spotsAvailable = details.spotsAvailable,
                    startTime = cached.startTime,
                    endTime = cached.endTime,
                    isJoined = details.isJoined,
                    isFull = details.isFull,
                    imageUrl = instructorImage,
                    description = details.description,
                    cost = details.cost,
                    totalSpots = details.totalSpots,
                    cancelPolicy = details.cancelPolicy
                )
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

    override suspend fun bookTraining(training: TrainingEntity): Result<Booking> {
        return try {
            val result = webScraper.bookTraining(training)
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

    override suspend fun cancelBooking(training: TrainingEntity): Result<Booking> {
        return try {
            val result = webScraper.cancelBooking(training)
            result.map { bookingDto ->
                val booking = bookingDto.toDomain()
                dao.deleteBookingByTrainingId(training.id)
                val current = bookingsFlow.value.toMutableList()
                current.removeAll { it.trainingId == training.id }
                bookingsFlow.value = current
                booking
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getMyBookings(): Flow<List<Booking>> = bookingsFlow

    override suspend fun refreshSchedule(): Result<Unit> {
        return try {
            webScraper.getSchedule()
            updateSyncStatus()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getSyncStatus(): Flow<SyncStatus> = syncStatusFlow

    private suspend fun updateSyncStatus() {
        val cacheControl = dao.getCacheControl()
        syncStatusFlow.value = SyncStatus(
            isSyncing = false,
            lastSyncTime = cacheControl?.lastSyncTime ?: 0L,
            nextSyncTime = cacheControl?.nextSyncTime ?: 0L,
            isDataStale = cacheControl?.let {
                System.currentTimeMillis() > it.lastSyncTime + it.staleAfterMillis
            } ?: true
        )
    }
}
