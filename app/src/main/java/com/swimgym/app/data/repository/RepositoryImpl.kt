package com.swimgym.app.data.repository

import android.content.Context
import com.swimgym.app.data.api.WebScraper
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.model.Mappers.toDomain
import com.swimgym.app.data.model.Mappers.toEntity
import com.swimgym.app.domain.model.*
import com.swimgym.app.domain.repository.SwodLevel
import com.swimgym.app.domain.repository.TrainingRepository
import com.swimgym.app.domain.repository.SyncStatus
import com.swimgym.app.util.BookingNotificationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale



class TrainingRepositoryImpl(
    private val context: Context,
    private val webScraper: WebScraper,
    private val dao: SwimGymDao,
    private val notificationManager: BookingNotificationManager,
) : TrainingRepository {

    private val bookingsFlow = MutableStateFlow<List<Booking>>(emptyList())
    private val syncStatusFlow = MutableStateFlow(SyncStatus())

   override fun getMyBookings(): Flow<List<Booking>> {
        return dao.getConfirmedBookings()
            .map { entities ->
                entities.map { entity ->
                    Booking(
                        id = 0,
                        trainingId = entity.id,
                        className = entity.title,
                        startTime = entity.startTime,
                        endTime = entity.endTime,
                        status = BookingStatus.CONFIRMED,
                        instructor = entity.instructor,
                        imageUrl = entity.imageUrl
                    )
                }
            }
    }


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
            val result = webScraper.getTrainingDetails(cached,context)

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

    override suspend fun bookTraining(training: TrainingEntity,context: Context): Result<Booking> {
        return try {
            val result = webScraper.bookTraining(training,context)
            result.map { bookingDto ->
                val booking = bookingDto.toDomain()
                dao.insertBooking(booking.toEntity())
                val current = bookingsFlow.value.toMutableList()
                current.add(booking)
                bookingsFlow.value = current
              notificationManager.showBookingConfirmation(
                     trainingName = booking.className,
                     classTime = formatTimestamp(booking.startTime),
                     classDate = formatDate(booking.startTime),
                     isScheduledBooking = false
                 )
                booking
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelBooking(training: TrainingEntity,context: Context): Result<Booking> {
        return try {
            val result = webScraper.cancelBooking(training,context)
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

  

   override suspend fun refreshSchedule(): Result<Unit> {
        return try {
            webScraper.getSchedule(context)
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

    companion object {
        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
}
