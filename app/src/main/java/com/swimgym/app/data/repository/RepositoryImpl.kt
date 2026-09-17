package com.swimgym.app.data.repository

import android.content.Context
import com.swimgym.app.data.api.VirtuagymApiClient
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.model.Mappers.toDomain
import com.swimgym.app.data.model.Mappers.toEntity
import com.swimgym.app.domain.model.*
import com.swimgym.app.domain.repository.SwodLevel
import com.swimgym.app.domain.repository.TrainingRepository
import com.swimgym.app.domain.repository.SyncStatus
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.swimgym.app.util.BookingNotificationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale


class TrainingRepositoryImpl(
    private val context: Context,
    private val apiClient: VirtuagymApiClient,
    private val dao: SwimGymDao,
    private val parser: com.swimgym.app.data.parser.ScheduleParser,
    private val calendarService: CalendarService,
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

            if (cachedTrainings.isEmpty() && isDataStale) {
                Log.d("TrainingRepo", "No cached trainings, triggering refresh")
                val refreshResult = refreshSchedule()
                if (refreshResult.isSuccess) {
                    cachedTrainings = dao.getAllTrainingsList()
                }
            }

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
            Log.e("TrainingRepo", "getSchedule failed", e)
            Result.failure(e)
        }
    }

    override suspend fun getTrainingDetails(trainingId: String): Result<Training> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("TrainingRepo", "getTrainingDetails called for trainingId: $trainingId")
                val cached = dao.getTrainingById(trainingId)
                if (cached == null) {
                    android.util.Log.e("TrainingRepo", "Cached training not found in DB: $trainingId")
                    return@withContext Result.failure(Exception("Cannot locate training for details"))
                }

                val instructor = dao.getInstructor(cached.instructor)
                android.util.Log.d("TrainingRepo", "Fetching details from API for: ${cached.title}")
                val result = getTrainingDetailsFromApi(cached)

                result.map { details ->
                    var instructorLink = ""
                    var instructorImage = ""

                    if (instructor != null) {
                        instructorLink = instructor.instructorLink
                        instructorImage = instructor.instructorImage
                    }

                    val domainTraining = Training(
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
                    android.util.Log.d("TrainingRepo", "Successfully fetched training details: ${domainTraining.title}")
                    domainTraining
                }
            } catch (e: Exception) {
                android.util.Log.e("TrainingRepo", "getTrainingDetails failed for $trainingId: ${e.message}", e)
                val cached = dao.getTrainingById(trainingId)
                if (cached != null) {
                    Result.success(cached.toDomain())
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    override suspend fun bookTraining(training: TrainingEntity): Result<Booking> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("BookTraining", "Building POST request for trainingId: ${training.id}, title: ${training.title}")
                val formBody = okhttp3.FormBody.Builder()
                    .add("action", "reserve_class")
                    .add("absent_reason", "")
                    .add("participant_name", "")
                    .add("participant_email", "")
                    .add("participant_notes", "")
                    .add("participant_id", "")
                    .add("present", "")
                    .add("participant_member_id", "")
                    .add("book_recurring", "")
                    .add("additional_note", "")
                    .add("class_name", training.title)
                    .add("class_time", training.classTime)
                    .add("class_date", training.classDate)
                    .add("send_email", "1")
                    .add("instance_of", "")
                    .add("cancel_recurring", "")
                    .add("email_participants_subject", "")
                    .add("email_participants_content", "")
                    .add("waiting_member_id", "")
                    .add("activity_id_filter", "")
                    .add("club_coach_id_filter", "")
                    .add("attendees", "0")
                    .build()

                val url = "https://swimgym.virtuagym.com/classes/class/${training.id}?event_type=8"
                android.util.Log.d("BookTraining", "POST to URL: $url")
                android.util.Log.d("BookTraining", "Form params: action=reserve_class, class_name=${training.title}, class_date=${training.classDate}, class_time=${training.classTime}")

                val result = apiClient.postBooking(url, formBody)
                android.util.Log.d("BookTraining", "Response code: ${result.code}, isSuccessful: ${result.isSuccessful}")
                val responseBody = result.body?.string()
                if (responseBody != null) {
                    android.util.Log.d("BookTraining", "Response body: $responseBody")
                }

                if (result.isSuccessful || result.code == 302) {
                    val bookingDto = com.swimgym.app.data.model.BookingResponse(
                        id = 0,
                        trainingId = training.id,
                        userId = 0,
                        status = "confirmed",
                        className = training.title
                    )
                    val booking = bookingDto.toDomain()
                    android.util.Log.d("BookTraining", "HTTP request succeeded for: ${training.id}, waiting for API verification")
                    val current = bookingsFlow.value.toMutableList()
                    current.add(booking)
                    bookingsFlow.value = current
                    
                    Result.success(booking)
                } else {
                    android.util.Log.e("BookTraining", "Booking failed with code ${result.code} for trainingId: ${training.id}")
                    Result.failure(Exception("Booking failed: ${result.code}"))
                }
            } catch (e: Exception) {
                android.util.Log.e("BookTraining", "Exception during booking for ${training.id}", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun cancelBooking(training: TrainingEntity): Result<Booking> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("CancelBooking", "Building POST request for trainingId: ${training.id}, title: ${training.title}")
                val formBody = okhttp3.FormBody.Builder()
                    .add("action", "cancel_reserve_class")
                    .add("absent_reason", "unknown")
                    .add("participant_name", "")
                    .add("participant_email", "")
                    .add("participant_notes", "")
                    .add("participant_id", "")
                    .add("present", "")
                    .add("participant_member_id", "")
                    .add("book_recurring", "")
                    .add("additional_note", "")
                    .add("class_name", training.title)
                    .add("class_time", training.classTime)
                    .add("class_date", training.classDate)
                    .add("send_email", "1")
                    .add("instance_of", "")
                    .add("cancel_recurring", "")
                    .add("email_participants_subject", "")
                    .add("email_participants_content", "")
                    .add("waiting_member_id", "")
                    .add("activity_id_filter", "")
                    .add("club_coach_id_filter", "")
                    .add("attendees", "0")
                    .build()

                val url = "https://swimgym.virtuagym.com/classes/class/${training.id}?event_type=8"
                android.util.Log.d("CancelBooking", "POST to URL: $url")
                android.util.Log.d("CancelBooking", "Form params: action=cancel_reserve_class, class_name=${training.title}, class_date=${training.classDate}")

                val result = apiClient.postCancel(url, formBody)
                android.util.Log.d("CancelBooking", "Response code: ${result.code}, isSuccessful: ${result.isSuccessful}")
                val responseBody = result.body?.string()
                if (responseBody != null) {
                    android.util.Log.d("CancelBooking", "Response body: $responseBody")
                }

                if (result.isSuccessful || result.code == 302) {
                    val bookingDto = com.swimgym.app.data.model.BookingResponse(
                        id = 0,
                        trainingId = training.id,
                        userId = 1,
                        status = "cancelled"
                    )
                    val booking = bookingDto.toDomain()
                    android.util.Log.d("CancelBooking", "Deleting booking from DB for trainingId: ${training.id}")
                    dao.deleteBookingByTrainingId(training.id)
                    val current = bookingsFlow.value.toMutableList()
                    current.removeAll { it.trainingId == training.id }
                    bookingsFlow.value = current

                    calendarService.removeTrainingFromCalendar(training)

                    android.util.Log.d("CancelBooking", "Cancellation successful for: ${training.id}")
                    Result.success(booking)
                } else {
                    android.util.Log.e("CancelBooking", "Cancellation failed with code ${result.code} for trainingId: ${training.id}")
                    Result.failure(Exception("Cancel failed: ${result.code}"))
                }
            } catch (e: Exception) {
                android.util.Log.e("CancelBooking", "Exception during cancellation for ${training.id}", e)
                Result.failure(e)
            }
        }
    }


    override suspend fun refreshSchedule(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("TrainingRepo", "Starting schedule fetch")
                val existingIds = dao.getAllTrainingIds()
                Log.d("TrainingRepo", "Found ${existingIds.size} existing trainings")
                val trainings = fetchAndSaveSchedule()
                Log.d("TrainingRepo", "Fetched ${trainings.size} trainings")
                if (trainings.isNotEmpty()) {
                    dao.insertTrainings(trainings.map { it.toEntity() })
                    Log.d("TrainingRepo", "Inserted ${trainings.size} trainings into DB")
                    val newIds = trainings.map { it.id }.toSet()
                    val deletedIds = existingIds.filter { it !in newIds }
                    Log.d("TrainingRepo", "${deletedIds.size} deleted trainings to remove")
                    for (id in deletedIds) {
                        dao.deleteTrainingById(id)
                        Log.d("TrainingRepo", "Deleted training: $id")
                    }
                } else {
                    Log.d("TrainingRepo", "No trainings fetched, skipping insert")
                }
                updateSyncStatus()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("TrainingRepo", "refreshSchedule failed", e)
                Result.failure(e)
            }
        }
    }

    override fun getSyncStatus(): Flow<SyncStatus> = syncStatusFlow

    private suspend fun fetchAndSaveSchedule(): List<com.swimgym.app.data.model.TrainingDto> {
        val trainings = mutableListOf<com.swimgym.app.data.model.TrainingDto>()
        val date = java.util.Calendar.getInstance()
        val now = date.timeInMillis / 1000

        for (weekOffset in 0 until 3) {
            val formattedDate = LocalDate.now(ZoneId.systemDefault())
                .minusWeeks((1 - weekOffset).toLong())
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))

            val url = "https://swimgym.virtuagym.com/classes/week/$formattedDate?event_type=8"

            try {
                val doc = apiClient.fetchHtml(url)
                val weeklyTrainings = parser.parseSchedule(doc, weekOffset, now, apiClient)
                trainings.addAll(weeklyTrainings)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return trainings
    }

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

    private suspend fun getTrainingDetailsFromApi(training: TrainingEntity): Result<com.swimgym.app.data.local.entity.TrainingEntity> {
        android.util.Log.d("TrainingRepo", "Fetching training details from API for: ${training.id} - ${training.title}")
        val trainingId = training.id
        return try {
            val result = parser.parseTrainingDetails(trainingId, training, apiClient)
            android.util.Log.d("TrainingRepo", "API returned updated details for: ${result.title}")
            dao.insertTraining(result)
            Result.success(result)
        } catch (e: Exception) {
            android.util.Log.e("TrainingRepo", "Failed to fetch API details for trainingId: $trainingId - ${e.message}", e)
            Result.failure(e)
        }
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
