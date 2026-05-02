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
            // Clear cookies from cache on logout
            sessionRepo.saveCookies(emptyMap())
            userFlow.value = null
            dao.clearUsers()
            dao.clearBookings()
            dao.clearTrainings()
            dao.clearCacheControl()
            Result.success(Unit)
        } catch (e: Exception) {
            sessionRepo.clearSession()
            sessionRepo.saveCookies(emptyMap())
            userFlow.value = null
            dao.clearUsers()
            dao.clearBookings()
            dao.clearTrainings()
            dao.clearCacheControl()
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
    private val syncStatusFlow = MutableStateFlow(SyncStatus())

    override suspend fun getSchedule(level: SwodLevel, hideFullyBooked: Boolean, startDate: String?): Result<List<Training>> {
        return try {
            var cachedTrainings = dao.getAllTrainingsList()
            val cacheControl = dao.getCacheControl()
            var isDataStale = cacheControl?.let {
                System.currentTimeMillis() > it.lastSyncTime + it.staleAfterMillis 
            } ?: true
            isDataStale=false
            if (cachedTrainings.isEmpty() || isDataStale) {

                webScraper.getSchedule()
                updateSyncStatus()
                cachedTrainings = dao.getAllTrainingsList()
            }
            val filteredtrainings = cachedTrainings
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
            Result.success(filteredtrainings)

        } catch (e: Exception) {
          return Result.failure(e)
        }
    }

    override suspend fun getTrainingDetails(trainingId: String): Result<Training> {
        return try {
            val cached = dao.getTrainingById(trainingId)
            if (cached == null) {
                return Result.failure(Exception("cannot locate training for details"))
            }
            val instructor = dao.getInstructor(cached.instructor)

            val result = webScraper.getTrainingDetails(trainingId)


            result.map { details ->
                var training = Training(
                    id = details.id,
                    title = details.title,
                    instructor = details.instructor,
                    //instructorLink = instructor?.instructorLink,
                    location = details.location,
                    spotsAvailable = details.spotsAvailable,
                    startTime = cached.startTime,
                    endTime = cached.endTime,
                    isJoined = details.isJoined,
                    isFull = details.isFull,
                    //imageUrl = instructor?.instructorImage,
                    description = details.description,
                    cost = details.cost,
                    totalSpots = details.totalSpots,
                    cancelPolicy = details.cancelPolicy
                )
                //val trainer = dao.getInstructor(details.instructor)

                dao.insertTraining(training.toEntity())
                //training.imageUrl=instructor.instructorImage
                //training.instructorLink=instructor.instructorLink
                var instructorLink = ""
                var instructorImage = ""

                if (instructor!=null) {
                    instructorLink = instructor.instructorLink
                    instructorImage = instructor.instructorImage
                }
                training = Training(
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

    override suspend fun bookTraining(training: Training): Result<Booking> {
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

    override suspend fun refreshSchedule(): Result<Unit> {
        return try {
            val result = webScraper.getSchedule()
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