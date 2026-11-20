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
            val startSeconds = startDate?.let {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time?.div(1000)
            }

            if (startDate != null) {
                // A failed "load more weeks" request must not discard the
                // schedule already cached locally.
                try {
                    val weekTrainings = fetchWeek(startDate)
                    if (weekTrainings.isNotEmpty()) {
                        dao.insertTrainings(weekTrainings.map { it.toEntity() })
                    }
                } catch (e: Exception) {
                    Log.w("TrainingRepo", "Failed to load requested week $startDate", e)
                }
            }

            var cachedTrainings = dao.getAllTrainingsList()

            if (cachedTrainings.isEmpty()) {
                Log.d("TrainingRepo", "No cached trainings, triggering refresh")
                val refreshResult = refreshSchedule()
                // A partial refresh still persists the weeks that succeeded.
                // Re-read regardless of the result and only fail when nothing
                // usable was stored.
                cachedTrainings = dao.getAllTrainingsList()
                if (refreshResult.isFailure && cachedTrainings.isEmpty()) {
                    return Result.failure(
                        refreshResult.exceptionOrNull() ?: Exception("Schedule refresh failed")
                    )
                }
            }

            val filteredTrainings = cachedTrainings
                .map { it.toDomain() }
                .filter { startSeconds == null || it.startTime >= startSeconds }
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

                android.util.Log.d("TrainingRepo", "Fetching details from API for: ${cached.title}")
                val result = getTrainingDetailsFromApi(cached)

                result.map { details ->
                    var instructorLink = ""
                    var instructorImage = ""

                    // Look up the instructor by the name returned by the API, not
                    // the cached one, which may have changed since the last sync.
                    val instructor = dao.getInstructor(details.instructor)
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
                // Never present stale cached data as freshly verified details: the
                // booking flow uses this call to confirm a booking actually succeeded.
                Result.failure(e)
            }
        }
    }

    override suspend fun bookTraining(training: TrainingEntity): Result<Booking> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("BookTraining", "Booking trainingId: ${training.id}, title: ${training.title}")
                val bookResult = apiClient.bookTraining(training)
                bookResult.fold(
                    onSuccess = {
                        android.util.Log.d("BookTraining", "Booking succeeded for: ${training.id}")
                        val bookingDto = com.swimgym.app.data.model.BookingResponse(
                            id = 0,
                            trainingId = training.id,
                            status = "confirmed",
                            className = training.title
                        )
                        val booking = bookingDto.toDomain()
                        val current = bookingsFlow.value.toMutableList()
                        current.add(booking)
                        bookingsFlow.value = current
                        Result.success(booking)
                    },
                    onFailure = { e ->
                        android.util.Log.e("BookTraining", "Booking failed for ${training.id}", e)
                        Result.failure<Booking>(e)
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("BookTraining", "Exception during booking for ${training.id}", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun cancelBooking(training: TrainingEntity): Result<Booking> {
        return withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("CancelBooking", "Cancelling trainingId: ${training.id}, title: ${training.title}")
                val cancelResult = apiClient.cancelTraining(training)
                val booking = Booking(
                    id = 0,
                    trainingId = training.id,
                    className = training.title,
                    startTime = training.startTime,
                    endTime = training.endTime,
                    status = com.swimgym.app.domain.model.BookingStatus.CANCELLED,
                    instructor = training.instructor
                )
                cancelResult.fold(
                    onSuccess = {
                        android.util.Log.d("CancelBooking", "Cancellation succeeded for: ${training.id}")
                        calendarService.removeTrainingByTimeAndTitle(training.startTime, training.title)
                        dao.deleteBookingByTrainingId(training.id)
                        val current = bookingsFlow.value.toMutableList()
                        current.removeAll { it.trainingId == training.id }
                        bookingsFlow.value = current
                        Result.success(booking)
                    },
                    onFailure = { e ->
                        android.util.Log.e("CancelBooking", "Cancellation failed for ${training.id}", e)
                        Result.failure<Booking>(e)
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("CancelBooking", "Exception during cancellation for ${training.id}", e)
                Result.failure(e)
            }
        }
    }


    override suspend fun refreshSchedule(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            syncStatusFlow.value = SyncStatus(isSyncing = true)
            try {
                Log.d("TrainingRepo", "Starting schedule fetch")
                val fetch = fetchAndSaveSchedule()
                Log.d("TrainingRepo", "Fetched ${fetch.trainings.size} trainings")
                if (fetch.trainings.isNotEmpty()) {
                    dao.insertTrainings(fetch.trainings.map { it.toEntity() })
                    Log.d("TrainingRepo", "Inserted ${fetch.trainings.size} trainings into DB")
                    // A partial fetch must never delete classes from the weeks that
                    // failed to load; they are still valid and may be booked.
                    if (fetch.allWeeksSucceeded) {
                        val newIds = fetch.trainings.map { it.id }.toSet()
                        // Never delete a class the user already joined (or that has a
                        // booking record): those can be outside the fetched 3-week
                        // window yet remain confirmed server-side.
                        val protectedIds = (dao.getJoinedTrainingIds() + dao.getBookedTrainingIds()).toSet()
                        val deletedIds = dao.getAllTrainingIds().filter { it !in newIds && it !in protectedIds }
                        Log.d("TrainingRepo", "${deletedIds.size} deleted trainings to remove")
                        for (id in deletedIds) {
                            dao.deleteTrainingById(id)
                            dao.deleteBookingByTrainingId(id)
                            Log.d("TrainingRepo", "Deleted training: $id")
                        }
                    }
                } else {
                    Log.d("TrainingRepo", "No trainings fetched, skipping insert")
                }
                if (fetch.allWeeksSucceeded) {
                    Result.success(Unit)
                } else {
                    Result.failure(fetch.error ?: Exception("Partial schedule fetch"))
                }
            } catch (e: Exception) {
                Log.e("TrainingRepo", "refreshSchedule failed", e)
                Result.failure(e)
            } finally {
                syncStatusFlow.value = SyncStatus(isSyncing = false)
            }
        }
    }

    private data class ScheduleFetch(
        val trainings: List<com.swimgym.app.data.model.TrainingDto>,
        val allWeeksSucceeded: Boolean,
        val error: Exception?
    )

    override fun getSyncStatus(): Flow<SyncStatus> = syncStatusFlow

    private suspend fun fetchAndSaveSchedule(): ScheduleFetch {
        val trainings = mutableListOf<com.swimgym.app.data.model.TrainingDto>()
        var allWeeksSucceeded = true
        var firstError: Exception? = null

        for (weekOffset in 0 until 3) {
            // Anchor on this week's Monday first, then step by whole weeks. Applying
            // nextOrSame after minusWeeks made the range depend on today's weekday.
            val formattedDate = LocalDate.now(ZoneId.systemDefault())
                .with(DayOfWeek.MONDAY)
                .minusWeeks((1 - weekOffset).toLong())

            try {
                trainings.addAll(fetchWeek(formattedDate.toString(), weekOffset))
            } catch (e: Exception) {
                allWeeksSucceeded = false
                if (firstError == null) firstError = e
                Log.e("TrainingRepo", "Failed to fetch schedule week $formattedDate", e)
            }
        }

        return ScheduleFetch(trainings, allWeeksSucceeded, firstError)
    }

    private suspend fun fetchWeek(date: String, weekOffset: Int = 0): List<com.swimgym.app.data.model.TrainingDto> {
        val now = System.currentTimeMillis() / 1000
        val doc = apiClient.fetchHtml("https://swimgym.virtuagym.com/classes/week/$date?event_type=8")
        return parser.parseSchedule(doc, weekOffset, now, apiClient)
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
