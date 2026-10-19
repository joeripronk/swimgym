package com.swimgym.app.data.repository

import android.content.Context
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.util.AlarmScheduler
import com.swimgym.app.util.BookingNotificationManager
import com.swimgym.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BookingScheduler {
    suspend fun checkBookings()
    suspend fun processSingleBooking(bookingId: Long): Boolean
    suspend fun checkCalendarConflicts()
    suspend fun simulateBooking(booking: ScheduledBooking): DryRunResult
}

sealed interface DryRunResult {
    data class Ok(val training: TrainingEntity, val bookable: Boolean) : DryRunResult
    data class Renamed(val newName: String) : DryRunResult
    data class NotAvailable(val reason: String) : DryRunResult
    data class Conflict(
        val training: TrainingEntity,
        val conflictTitle: String,
        val conflicts: List<CalendarEventInfo>,
        val bookable: Boolean
    ) : DryRunResult
}

class BookingSchedulerImpl(
    private val dao: SwimGymDao,
    private val scheduledBookingRepo: ScheduledBookingRepository,
    private val notificationManager: BookingNotificationManager,
    private val alarmScheduler: AlarmScheduler,
    private val apiClient: com.swimgym.app.data.api.VirtuagymApiClient,
    private val context: Context,
    private val calendarService: CalendarService,
    private val sessionRepository: SessionRepository
) : BookingScheduler {

    companion object {
        private const val CALENDAR_CONFLICT_BUFFER_SECONDS = 60 * 60
        private const val WEEK_SECONDS = 7 * 86400
        private const val MIN_LEAD_SECONDS = 2 * 86400
    }

    override suspend fun checkBookings() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000
        val bookings = scheduledBookingRepo.getAllBookings()
        if (bookings.isNullOrEmpty()) return@withContext

        val activeBookings = bookings.filter { it.status == ScheduledBookingStatus.ACTIVE }

        activeBookings.forEach { booking ->
            var adjustedStartTime = booking.startTime
            while (adjustedStartTime < now) {
                adjustedStartTime += WEEK_SECONDS
            }
            val adjustedBooking = booking.copy(startTime = adjustedStartTime)
            if (adjustedStartTime != booking.startTime) {
                scheduledBookingRepo.saveBooking(adjustedBooking)
            }
            when (val dryRun = simulateBooking(adjustedBooking)) {
                is DryRunResult.Ok -> if (dryRun.bookable) attemptBooking(adjustedBooking, dryRun.training, scheduleNextAlarm = false)
                is DryRunResult.Conflict -> if (dryRun.bookable) attemptBooking(adjustedBooking, dryRun.training, scheduleNextAlarm = false)
                is DryRunResult.Renamed -> notificationManager.showTrainingRenamed(booking.className, dryRun.newName)
                is DryRunResult.NotAvailable -> Unit
            }
        }
    }

    override suspend fun processSingleBooking(bookingId: Long): Boolean = withContext(Dispatchers.IO) {
        val booking = scheduledBookingRepo.getBooking(bookingId) ?: return@withContext false
        if (booking.status != ScheduledBookingStatus.ACTIVE) return@withContext false

        val now = System.currentTimeMillis() / 1000
        var adjustedStartTime = booking.startTime
        while (adjustedStartTime < now) {
            adjustedStartTime += WEEK_SECONDS
        }
        val updatedBooking = booking.copy(startTime = adjustedStartTime)
        scheduledBookingRepo.saveBooking(updatedBooking)

        when (val dryRun = simulateBooking(updatedBooking)) {
            is DryRunResult.Ok -> dryRun.bookable && attemptBooking(updatedBooking, dryRun.training, scheduleNextAlarm = true)
            is DryRunResult.Conflict -> dryRun.bookable && attemptBooking(updatedBooking, dryRun.training, scheduleNextAlarm = true)
            is DryRunResult.Renamed -> {
                notificationManager.showTrainingRenamed(booking.className, dryRun.newName)
                true
            }
            is DryRunResult.NotAvailable -> false
        }
    }

    override suspend fun simulateBooking(booking: ScheduledBooking): DryRunResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000
        var occurrence = booking.startTime
        while (occurrence < now) {
            occurrence += WEEK_SECONDS
        }

        val trainings = dao.getAllTrainingsList()
        if (trainings.isNullOrEmpty()) {
            return@withContext DryRunResult.NotAvailable("no cached trainings")
        }
        val training = trainings.find { it.startTime == occurrence }
            ?: return@withContext DryRunResult.NotAvailable("class not in schedule")
        if (!training.title.equals(booking.className, ignoreCase = true)) {
            return@withContext DryRunResult.Renamed(training.title)
        }

        val duration = if (training.endTime > training.startTime) training.endTime - training.startTime else 3600L
        val conflicts = findCalendarConflict(
            startSeconds = occurrence,
            endSeconds = occurrence + duration,
            excludeEventId = training.eventId.ifBlank { null },
            excludeTitle = training.title
        )
        if (conflicts.isNotEmpty()) {
            val bookable = occurrence >= now + MIN_LEAD_SECONDS && occurrence <= now + WEEK_SECONDS
            return@withContext DryRunResult.Conflict(
                training = training,
                conflictTitle = conflicts.first().title.ifBlank { "a calendar event" },
                conflicts = conflicts,
                bookable = bookable
            )
        }
        val bookable = occurrence >= now + MIN_LEAD_SECONDS && occurrence <= now + WEEK_SECONDS
        DryRunResult.Ok(training, bookable)
    }

    override suspend fun checkCalendarConflicts() = withContext(Dispatchers.IO) {
        if (!sessionRepository.getCalendarConflictCheckEnabled()) return@withContext
        if (!PermissionHelper.hasCalendarReadPermission(context)) return@withContext

        val now = System.currentTimeMillis() / 1000
        val bookings = scheduledBookingRepo.getAllBookings()
            .filter { it.status == ScheduledBookingStatus.ACTIVE }
        val farOutBookings = bookings.filter { booking ->
            var occurrence = booking.startTime
            while (occurrence < now) {
                occurrence += WEEK_SECONDS
            }
            occurrence > now + WEEK_SECONDS
        }
        if (farOutBookings.isEmpty()) {
            if (sessionRepository.getConflictNotifiedIds().isNotEmpty()) {
                sessionRepository.saveConflictNotifiedIds(emptySet())
            }
            return@withContext
        }

        val alreadyNotified = sessionRepository.getConflictNotifiedIds()
        val notifiedKeys = mutableSetOf<String>()
        farOutBookings.forEach { booking ->
            when (val dryRun = simulateBooking(booking)) {
                is DryRunResult.Conflict -> {
                    val key = "c-${booking.id}"
                    notifiedKeys.add(key)
                    if (key !in alreadyNotified) {
                        notificationManager.showScheduledBookingConflictWarning(
                            bookingId = booking.id,
                            trainingName = booking.className,
                            classDate = dryRun.training.classDate,
                            classTime = dryRun.training.classTime,
                            instructor = dryRun.training.instructor.ifBlank { booking.instructor },
                            conflictTitle = dryRun.conflictTitle
                        )
                    }
                }
                is DryRunResult.NotAvailable -> {
                    val key = "na-${booking.id}"
                    notifiedKeys.add(key)
                    if (key !in alreadyNotified) {
                        notificationManager.showScheduledBookingNotAvailable(
                            bookingId = booking.id,
                            trainingName = booking.className,
                            reason = dryRun.reason
                        )
                    }
                }
                is DryRunResult.Renamed -> {
                    val key = "r-${booking.id}"
                    notifiedKeys.add(key)
                    if (key !in alreadyNotified) {
                        notificationManager.showTrainingRenamed(booking.className, dryRun.newName)
                    }
                }
                is DryRunResult.Ok -> Unit
            }
        }
        sessionRepository.saveConflictNotifiedIds(notifiedKeys)
    }

    private suspend fun attemptBooking(
        booking: ScheduledBooking,
        training: TrainingEntity,
        scheduleNextAlarm: Boolean
    ): Boolean {
        val res = getTrainingDetails(training)
        if (!res.isSuccess) {
            showRetryNotification(booking)
            rescheduleAlarmForRetry(booking)
            return false
        }
        val updtraining = res.getOrThrow()
        if (updtraining.isJoined) {
            scheduledBookingRepo.incrementBookingCount(booking.id, training)
            val maxRepeat = booking.maxRepeatCount
            if (maxRepeat != null && booking.bookedCount + 1 >= maxRepeat) {
                scheduledBookingRepo.completeBooking(booking.id)
            }
            return true
        }
        if (updtraining.isFull) {
            return false
        }
        val bookResult = apiClient.bookTraining(training)
        return bookResult.fold(
            onSuccess = {
                scheduledBookingRepo.incrementBookingCount(booking.id, training)
                val nextBooking = booking.copy(startTime = training.startTime + WEEK_SECONDS)
                scheduledBookingRepo.saveBooking(nextBooking)
                val maxRepeat = booking.maxRepeatCount
                if (maxRepeat != null && booking.bookedCount + 1 >= maxRepeat) {
                    scheduledBookingRepo.completeBooking(booking.id)
                } else if (scheduleNextAlarm) {
                    alarmScheduler.scheduleBooking(booking.id, nextBooking.startTime * 1000)
                }
                notificationManager.showBookingConfirmation(
                    trainingName = training.title,
                    classTime = training.classTime,
                    classDate = training.classDate,
                    instructor = training.instructor,
                    isScheduledBooking = true,
                    location = training.location,
                    spotsAvailable = training.spotsAvailable,
                    bookingCount = booking.bookedCount,
                    maxRepeatCount = booking.maxRepeatCount
                )
                true
            },
            onFailure = {
                showRetryNotification(booking)
                rescheduleAlarmForRetry(booking)
                false
            }
        )
    }

    private suspend fun findCalendarConflict(
        startSeconds: Long,
        endSeconds: Long,
        excludeEventId: String?,
        excludeTitle: String
    ): List<CalendarEventInfo> {
        if (!sessionRepository.getCalendarConflictCheckEnabled()) return emptyList()
        if (!PermissionHelper.hasCalendarReadPermission(context)) return emptyList()
        val windowStartMillis = (startSeconds - CALENDAR_CONFLICT_BUFFER_SECONDS) * 1000
        val windowEndMillis = (endSeconds + CALENDAR_CONFLICT_BUFFER_SECONDS) * 1000
        return calendarService.findConflictingEvents(
            startMillis = windowStartMillis,
            endMillis = windowEndMillis,
            excludeEventId = excludeEventId,
            excludeTitle = excludeTitle
        )
    }

    private suspend fun rescheduleAlarmForRetry(booking: ScheduledBooking) {
        val now = System.currentTimeMillis() / 1000
        val windowOpen = booking.startTime > now + 2 * 86400 && booking.startTime < now + 7 * 86400
        if (!windowOpen) {
            var startSeconds = booking.startTime
            while (startSeconds < now + 7 * 86400) {
                startSeconds += 7 * 86400
            }
            val nextAlarmTime = (startSeconds - 7 * 86400 + 5 * 60) * 1000L
            val nowMillis = System.currentTimeMillis()
            if (nextAlarmTime > nowMillis) {
                alarmScheduler.scheduleBooking(booking.id, nextAlarmTime)
            }
            return
        }
        val retryTime = System.currentTimeMillis() + 15 * 60000L
        alarmScheduler.scheduleBooking(booking.id, retryTime)
    }

    private suspend fun showRetryNotification(booking: ScheduledBooking) = withContext(Dispatchers.IO) {
        val training = dao.getTrainingById(booking.trainingId)
        val classDate = training?.classDate ?: ""
        val classTime = training?.classTime ?: ""
        val className = training?.title ?: booking.className
        val instructor = training?.instructor ?: ""
        notificationManager.showBookingRetry(booking.id, className, classDate, classTime, instructor)
    }

    private suspend fun getTrainingDetails(training: TrainingEntity): Result<TrainingEntity> = withContext(Dispatchers.IO) {
        try {
            val trainingId = training.id
            val doc = apiClient.fetchHtml("https://swimgym.virtuagym.com/classes/class/$trainingId?embedded=0")

            val title =
                doc.selectFirst(".modal-title-replacement, .class-info .class-name")?.text() ?: ""
            if (title.isNullOrEmpty()) {
                throw Exception("failed to fetch data")
            }
            val instructorLink =
                doc.selectFirst(".event-details-icon a[href^=/userid]")?.attr("href") ?: ""
            val instructor =
                doc.selectFirst(".event-details-icon a[href^=/userid]")?.text() ?: "TBA"
            val location =
                doc.selectFirst("div.event-details-icons:nth-child(3) > div.event-details-icon:nth-child(2) > div.icon-text:nth-child(2)")
                    ?.text() ?: "SwimGym"
            val spotsText =
                doc.selectFirst("div.event-details-icons:nth-child(2) > div.event-details-icon:nth-child(3) > div.icon-text:nth-child(2)")
                    ?.text() ?: "invalid"
            val imageUrl = doc.selectFirst(".event-image-holder img")?.attr("src") ?: ""
            val description = doc.selectFirst(".event-description-holder")?.text() ?: ""
            val cost = doc.selectFirst(".event-details-icon.ticket-star .icon-text")?.text() ?: ""
            val isJoined = doc.selectFirst(".booking-text.green") != null
            val cancelPolicy =
                doc.selectFirst(".event-actions > div:not(.booking-text)")?.text() ?: ""

            val spotsParts = spotsText.split("/").map { it.trim() }
            val spotsTaken = spotsParts.getOrNull(0)?.toIntOrNull() ?: 0
            val totalSpots = spotsParts.getOrNull(1)?.toIntOrNull() ?: 0

            val spotsAvailable = totalSpots - spotsTaken
            val isFull = spotsAvailable == 0

            if (imageUrl.isNotEmpty()) {
                dao.insertInstructor(
                    com.swimgym.app.data.local.entity.InstructorEntity(
                        instructorName = instructor,
                        instructorLink = instructorLink,
                        instructorImage = imageUrl
                    )
                )
            }

            val tupdate = TrainingEntity(
                id = trainingId,
                instructor = instructor,
                startTime = training.startTime,
                endTime = training.endTime,
                classDate = training.classDate,
                classTime = training.classTime,
                title = title,
                location = location,
                spotsAvailable = spotsAvailable,
                totalSpots = totalSpots,
                description = description,
                isFull = isFull,
                cost = cost,
                isJoined = isJoined,
                imageUrl = imageUrl,
                cancelPolicy = cancelPolicy,
                eventId = training.eventId,
                calendarId = training.calendarId
            )

            dao.insertTraining(tupdate)
            Result.success(tupdate)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
