package com.swimgym.app.data.repository

import android.content.Context
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.util.AlarmScheduler
import com.swimgym.app.util.BookingNotificationManager
import com.swimgym.app.util.PermissionHelper
import com.swimgym.app.util.addDaysToSeconds
import com.swimgym.app.util.wallClockDeltaSeconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BookingScheduler {
    suspend fun checkBookings()
    suspend fun processSingleBooking(bookingId: Long): Boolean
    suspend fun checkCalendarConflicts()
    suspend fun simulateBooking(booking: ScheduledBooking): DryRunResult
    suspend fun stopRetries(bookingId: Long)
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
    private val sessionRepository: SessionRepository,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 }
) : BookingScheduler {

    companion object {
        private const val CALENDAR_CONFLICT_BUFFER_SECONDS = 60 * 60
        private const val WEEK_SECONDS = 7 * 86400
        private const val MIN_LEAD_SECONDS = 2 * 86400
        const val MAX_RETRIES = 20
    }

    override suspend fun checkBookings() = withContext(Dispatchers.IO) {
        val now = nowSeconds()
        val bookings = scheduledBookingRepo.getAllBookings()
        if (bookings.isNullOrEmpty()) return@withContext

        // A booking that is already retrying belongs to its own 15-minute alarm.
        // Letting the twice-daily sync attempt it too would burn the retry budget
        // twice as fast and replace the alarm the retry just scheduled. If the
        // alarm was dropped (Doze/OEM), its deadline is in the past and the sync
        // acts as the fallback instead.
        val activeBookings = bookings.filter { booking ->
            if (booking.status != ScheduledBookingStatus.ACTIVE) return@filter false
            if (booking.retryCount == 0) return@filter true
            // Bookings persisted before nextRetryAt existed have no deadline;
            // let the sync act as their fallback too.
            val deadline = booking.nextRetryAt ?: return@filter true
            deadline <= now
        }

        activeBookings.forEach { booking ->
            val adjustedStartTime = nextOccurrence(booking.startTime, now)
            val adjustedBooking = if (adjustedStartTime != booking.startTime) {
                scheduledBookingRepo.advanceToOccurrence(booking.id, adjustedStartTime)
                booking.copy(startTime = adjustedStartTime, retryCount = 0, lastFailureReason = null)
            } else booking
            when (val dryRun = simulateBooking(adjustedBooking)) {
            is DryRunResult.Ok -> if (dryRun.bookable) attemptBooking(adjustedBooking, dryRun.training, scheduleNextAlarm = true)
            is DryRunResult.Conflict -> if (dryRun.bookable) attemptBooking(adjustedBooking, dryRun.training, scheduleNextAlarm = true)
                is DryRunResult.Renamed -> notificationManager.showTrainingRenamed(booking.className, dryRun.newName)
                is DryRunResult.NotAvailable -> Unit
            }
        }
    }

    override suspend fun processSingleBooking(bookingId: Long): Boolean = withContext(Dispatchers.IO) {
        val booking = scheduledBookingRepo.getBooking(bookingId) ?: return@withContext false
        if (booking.status != ScheduledBookingStatus.ACTIVE) return@withContext false

        val now = nowSeconds()
        val adjustedStartTime = nextOccurrence(booking.startTime, now)
        val updatedBooking = if (adjustedStartTime != booking.startTime) {
            scheduledBookingRepo.advanceToOccurrence(booking.id, adjustedStartTime)
            booking.copy(startTime = adjustedStartTime, retryCount = 0, lastFailureReason = null)
        } else booking

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
        val now = nowSeconds()
        val occurrence = nextOccurrence(booking.startTime, now)

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
        val bookable = isBookable(occurrence, now)
        if (conflicts.isNotEmpty()) {
            return@withContext DryRunResult.Conflict(
                training = training,
                conflictTitle = conflicts.first().title.ifBlank { "a calendar event" },
                conflicts = conflicts,
                bookable = bookable
            )
        }
        DryRunResult.Ok(training, bookable)
    }

    override suspend fun checkCalendarConflicts() = withContext(Dispatchers.IO) {
        if (!sessionRepository.getCalendarConflictCheckEnabled()) return@withContext
        if (!PermissionHelper.hasCalendarReadPermission(context)) return@withContext

        val now = nowSeconds()
        val bookings = scheduledBookingRepo.getAllBookings()
            .filter { it.status == ScheduledBookingStatus.ACTIVE }
        val farOutBookings = bookings.mapNotNull { booking ->
            val occurrence = nextOccurrence(booking.startTime, now)
            if (wallClockDeltaSeconds(now, occurrence) > WEEK_SECONDS) {
                booking.copy(startTime = occurrence) to occurrence
            } else null
        }
        if (farOutBookings.isEmpty()) {
            if (sessionRepository.getConflictNotifiedIds().isNotEmpty()) {
                sessionRepository.saveConflictNotifiedIds(emptySet())
            }
            return@withContext
        }

        val alreadyNotified = sessionRepository.getConflictNotifiedIds()
        val notifiedKeys = mutableSetOf<String>()
        farOutBookings.forEach { (booking, occurrence) ->
            // Keys are bound to the specific weekly occurrence so a recurring
            // booking's conflict/renamed/unavailable state can be re-surfaced
            // for a new week once a previous week's state resolved.
            val weekKey = occurrence / WEEK_SECONDS
            when (val dryRun = simulateBooking(booking)) {
                is DryRunResult.Conflict -> {
                    val key = "c-${booking.id}-$weekKey"
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
                    val key = "na-${booking.id}-$weekKey"
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
                    val key = "r-${booking.id}-$weekKey"
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
            return handleFailure(booking, RetryFailureReason.from(res.exceptionOrNull()))
        }
        val updtraining = res.getOrThrow()
        if (updtraining.isJoined) {
            scheduledBookingRepo.incrementBookingCount(booking.id, training)
            val nextBooking = booking.copy(
                startTime = addDaysToSeconds(training.startTime, 7),
                instructor = training.instructor,
                trainingId = training.id,
                nextRetryAt = null
            )
            val maxRepeat = booking.maxRepeatCount
            if (maxRepeat != null && booking.bookedCount + 1 >= maxRepeat) {
                scheduledBookingRepo.completeBooking(booking.id)
            } else if (scheduleNextAlarm) {
                alarmScheduler.scheduleBooking(
                    booking.id,
                    alarmScheduler.bookingAlarmTimeMillis(nextBooking)
                )
            }
            return true
        }
        if (updtraining.isFull) {
            return handleFailure(booking, RetryFailureReason.CLASS_FULL)
        }
        val bookResult = try {
            apiClient.bookTraining(training)
        } catch (e: Exception) {
            Result.failure<Unit>(e)
        }
        return bookResult.fold(
            onSuccess = {
                val nextBooking = booking.copy(
                    bookedCount = booking.bookedCount + 1,
                    startTime = addDaysToSeconds(training.startTime, 7),
                    instructor = training.instructor,
                    trainingId = training.id,
                    retryCount = 0,
                    lastFailureReason = null,
                    nextRetryAt = null
                )
                scheduledBookingRepo.saveBooking(nextBooking)
                val maxRepeat = nextBooking.maxRepeatCount
                if (maxRepeat != null && nextBooking.bookedCount >= maxRepeat) {
                    scheduledBookingRepo.completeBooking(booking.id)
                } else if (scheduleNextAlarm) {
                    alarmScheduler.scheduleBooking(
                        booking.id,
                        alarmScheduler.bookingAlarmTimeMillis(nextBooking)
                    )
                }
                notificationManager.showBookingConfirmation(
                    trainingName = training.title,
                    classTime = training.classTime,
                    classDate = training.classDate,
                    instructor = training.instructor,
                    isScheduledBooking = true,
                    location = training.location,
                    spotsAvailable = training.spotsAvailable,
                    bookingCount = nextBooking.bookedCount,
                    maxRepeatCount = nextBooking.maxRepeatCount
                )
                true
            },
            onFailure = { error ->
                handleFailure(booking, RetryFailureReason.from(error))
            }
        )
    }

    override suspend fun stopRetries(bookingId: Long) = withContext(Dispatchers.IO) {
        scheduledBookingRepo.stopRetries(bookingId)
        alarmScheduler.cancelBooking(bookingId)
        notificationManager.dismissRetryNotification(bookingId)
    }

    /**
     * Records why this attempt failed, then either schedules another attempt or
     * stops the retry sequence once the limit is reached. Returns false in both
     * cases so callers can report the attempt as unsuccessful.
     *
     * Re-reads the booking before writing so a concurrent "Stop retrying" cannot
     * have its state overwritten or have a new alarm re-armed behind its back.
     */
    private suspend fun handleFailure(booking: ScheduledBooking, reason: RetryFailureReason): Boolean {
        val current = scheduledBookingRepo.getBooking(booking.id) ?: return false
        if (current.status != ScheduledBookingStatus.ACTIVE) return false
        val attempt = current.retryCount + 1
        scheduledBookingRepo.recordRetryFailure(current.id, attempt, reason)
        if (attempt >= MAX_RETRIES) {
            scheduledBookingRepo.stopRetries(current.id)
            alarmScheduler.cancelBooking(current.id)
            notificationManager.showRetriesStopped(
                bookingId = current.id,
                trainingName = current.className,
                reason = reason,
                retryCount = attempt
            )
            return false
        }
        showRetryNotification(current, reason, attempt)
        rescheduleAlarmForRetry(current)
        return false
    }

    /**
     * The first weekly occurrence at or after [now], measured on the local wall
     * clock. Adding calendar days via [addDaysToSeconds] preserves the time of
     * day, so across a DST transition a weekly class keeps its local start time;
     * advancing on epoch seconds instead would let a 6d23h/7d1h week drift the
     * booking window by an hour relative to [isBookable].
     */
    internal fun nextOccurrence(base: Long, now: Long): Long {
        var occurrence = base
        while (wallClockDeltaSeconds(now, occurrence) < 0) {
            occurrence = addDaysToSeconds(occurrence, 7)
        }
        return occurrence
    }

    /**
     * A class can be booked from 2 local days before it starts, until it is 7 local days
     * away. The window is measured on the local wall clock: 7 local days are 6d23h before
     * the spring transition and 7d1h after the autumn transition, so comparing raw epoch
     * seconds would drop classes that are exactly 7 days ahead around a transition.
     */
    private fun isBookable(occurrence: Long, now: Long): Boolean {
        val lead = wallClockDeltaSeconds(now, occurrence)
        return lead >= MIN_LEAD_SECONDS && lead <= WEEK_SECONDS
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
        val now = nowSeconds()
        val lead = wallClockDeltaSeconds(now, booking.startTime)
        val windowOpen = lead >= MIN_LEAD_SECONDS && lead <= WEEK_SECONDS
        val alarmTimeMillis = if (!windowOpen) {
            var startSeconds = booking.startTime
            while (wallClockDeltaSeconds(now, startSeconds) < WEEK_SECONDS) {
                startSeconds = addDaysToSeconds(startSeconds, 7)
            }
            addDaysToSeconds(startSeconds, -7) * 1000L + AlarmScheduler.BOOKING_LEAD_MILLIS
        } else {
            nowSeconds() * 1000 + 15 * 60000L
        }
        // Persist the deadline so the twice-daily sync can recover a dropped alarm.
        // Skip a booking that was stopped concurrently so no stray alarm is armed.
        if (scheduledBookingRepo.getBooking(booking.id)?.status != ScheduledBookingStatus.ACTIVE) return
        scheduledBookingRepo.setNextRetryAt(booking.id, alarmTimeMillis / 1000)
        alarmScheduler.scheduleBooking(booking.id, alarmTimeMillis)
    }

    private suspend fun showRetryNotification(
        booking: ScheduledBooking,
        reason: RetryFailureReason,
        retryCount: Int
    ) = withContext(Dispatchers.IO) {
        val training = dao.getTrainingById(booking.trainingId)
        val classDate = training?.classDate ?: ""
        val classTime = training?.classTime ?: ""
        val className = training?.title ?: booking.className
        val instructor = training?.instructor ?: ""
        notificationManager.showBookingRetry(
            bookingId = booking.id,
            trainingName = className,
            classDate = classDate,
            classTime = classTime,
            instructor = instructor,
            reason = reason,
            retryCount = retryCount,
            maxRetries = MAX_RETRIES
        )
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
                    ?.text()
            val imageUrl = doc.selectFirst(".event-image-holder img")?.attr("src") ?: ""
            val description = doc.selectFirst(".event-description-holder")?.text() ?: ""
            val cost = doc.selectFirst(".event-details-icon:has(.ticket-star) .icon-text")?.text() ?: ""
            val isJoined = doc.selectFirst(".booking-text.green") != null
            val cancelPolicy =
                doc.selectFirst(".event-actions > div:not(.booking-text)")?.text() ?: ""

            val spotsParts = spotsText?.split("/")?.map { it.trim() }
            val spotsTaken = spotsParts?.getOrNull(0)?.toIntOrNull()
            val totalSpotsParsed = spotsParts?.getOrNull(1)?.toIntOrNull()
            val spotsKnown = spotsTaken != null && totalSpotsParsed != null
            val totalSpots = if (spotsKnown) totalSpotsParsed!! else 0
            // Only treat a class as full when spots were actually parsed. An
            // unparseable value must not be mistaken for "0 spots left" and burn
            // the whole retry budget as CLASS_FULL after a markup change.
            val spotsAvailable = if (spotsKnown) maxOf(0, totalSpots - spotsTaken!!) else 0
            val explicitFull = doc.selectFirst(".full")?.text()?.trim()?.equals("VOL", ignoreCase = true) == true
            val isFull = !isJoined && (explicitFull || (spotsKnown && spotsAvailable == 0))

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
