package com.swimgym.app.data.repository

import android.content.Context
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.util.AlarmScheduler
import com.swimgym.app.util.BookingNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface BookingScheduler {
    suspend fun checkBookings()
    suspend fun processSingleBooking(bookingId: Long): Boolean
}

class BookingSchedulerImpl(
    private val dao: SwimGymDao,
    private val scheduledBookingRepo: ScheduledBookingRepository,
    private val notificationManager: BookingNotificationManager,
    private val alarmScheduler: AlarmScheduler,
    private val apiClient: com.swimgym.app.data.api.VirtuagymApiClient,
    private val context: Context
) : BookingScheduler {

    override suspend fun checkBookings() = withContext(Dispatchers.IO) {
        val calendar = java.util.Calendar.getInstance()
        val now = calendar.timeInMillis / 1000
        val bookings = scheduledBookingRepo.getAllBookings()
        if (bookings.isNullOrEmpty()) return@withContext

        var trainings = dao.getAllTrainingsList()
        if (trainings.isNullOrEmpty()) {
            return@withContext
        }

        val activeBookings = bookings.filter { it.status == ScheduledBookingStatus.ACTIVE }

        activeBookings.forEach { booking ->
            var adjustedStartTime = booking.startTime
            while (adjustedStartTime < now) {
                adjustedStartTime += 7 * 86400
            }
            if (adjustedStartTime != booking.startTime) {
                scheduledBookingRepo.saveBooking(booking.copy(startTime = adjustedStartTime))
            }
            val shouldBook = shouldBookForAlarm(booking.startTime, now)
            if (!shouldBook) return@forEach
            val training = getNextTraining(adjustedStartTime, booking.className, trainings) ?: return@forEach
            val res = getTrainingDetails(training)
            val updtraining = res.getOrThrow()
            if (updtraining.isJoined) {
                scheduledBookingRepo.incrementBookingCount(booking.id, training)
                return@forEach
            }
            if (updtraining.isFull) {
                return@forEach
            }
            val result = bookTraining(training)
            result.fold(
                onSuccess = {
                    scheduledBookingRepo.incrementBookingCount(booking.id, training)
                    val maxRepeat = booking.maxRepeatCount
                    if (maxRepeat != null && booking.bookedCount + 1 >= maxRepeat) {
                        scheduledBookingRepo.completeBooking(booking.id)
                    }
                    notificationManager.showBookingConfirmation(
                        trainingName = training.title,
                        classTime = training.classTime,
                        classDate = training.classDate,
                        isScheduledBooking = true
                    )
                },
                onFailure = { }
            )
        }
    }

    override suspend fun processSingleBooking(bookingId: Long): Boolean = withContext(Dispatchers.IO) {
        var booking = scheduledBookingRepo.getBooking(bookingId) ?: return@withContext false
        if (booking.status != ScheduledBookingStatus.ACTIVE) return@withContext false

        val trainings = dao.getAllTrainingsList() ?: run {
            showRetryNotification(booking)
            rescheduleAlarmForRetry(booking)
            return@withContext false
        }
        if (trainings.isEmpty()) {
            showRetryNotification(booking)
            rescheduleAlarmForRetry(booking)
            return@withContext false
        }

        val now = System.currentTimeMillis() / 1000
        var adjustedStartTime = booking.startTime
        while (adjustedStartTime < now) {
            adjustedStartTime += 7 * 86400
        }

        val updatedBooking = booking.copy(startTime = adjustedStartTime)
        scheduledBookingRepo.saveBooking(updatedBooking)

        if (!shouldBookForAlarm(booking.startTime, now)) {
            rescheduleAlarmForRetry(updatedBooking)
            return@withContext false
        }

        val training = getNextTraining(adjustedStartTime, booking.className, trainings) ?: run {
            showRetryNotification(booking)
            rescheduleAlarmForRetry(updatedBooking)
            return@withContext false
        }
        val res = getTrainingDetails(training)
        
        if (!res.isSuccess) {
            showRetryNotification(booking)
            rescheduleAlarmForRetry(updatedBooking)
            return@withContext false
        }

        val updtraining = res.getOrThrow()

        if (updtraining.isJoined) {
            scheduledBookingRepo.incrementBookingCount(bookingId, training)
            val maxRepeat = booking.maxRepeatCount
            if (maxRepeat != null && booking.bookedCount + 1 >= maxRepeat) {
                scheduledBookingRepo.completeBooking(bookingId)
            } else {
                rescheduleAlarmForRetry(updatedBooking)
            }
            return@withContext true
        }

        if (updtraining.isFull) {
            showRetryNotification(booking)
            rescheduleAlarmForRetry(updatedBooking)
            return@withContext false
        }

        val result = bookTraining(training)
        result.fold(
            onSuccess = {
                scheduledBookingRepo.incrementBookingCount(bookingId, training)

                alarmScheduler.scheduleBooking(bookingId, alarmScheduler.bookingAlarmTimeMillis(updatedBooking))

                val maxRepeat = booking.maxRepeatCount
                if (maxRepeat != null && booking.bookedCount + 1 >= maxRepeat) {
                    scheduledBookingRepo.completeBooking(bookingId)
                }

                notificationManager.showBookingConfirmation(
                    trainingName = training.title,
                    classTime = training.classTime,
                    classDate = training.classDate,
                    isScheduledBooking = true
                )
            },
            onFailure = {
                showRetryNotification(booking)
                rescheduleAlarmForRetry(updatedBooking)
            }
        )
        return@withContext true
    }

    private fun rescheduleAlarmForRetry(booking: ScheduledBooking) {
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
        val retryTime = System.currentTimeMillis() + 15 * 60 * 1000L
        alarmScheduler.scheduleBooking(booking.id, retryTime)
    }

    private suspend fun showRetryNotification(booking: ScheduledBooking) = withContext(Dispatchers.IO) {
        val training = dao.getTrainingById(booking.trainingId)
        val classDate = training?.classDate ?: ""
        val classTime = training?.classTime ?: ""
        val className = training?.title ?: booking.className
        notificationManager.showBookingRetry(booking.id, className, classDate, classTime)
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
            var totalSpots = spotsParts.getOrNull(1)?.toIntOrNull() ?: 0

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
                cancelPolicy = cancelPolicy
            )

            dao.insertTraining(tupdate)
            Result.success(tupdate)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun bookTraining(training: TrainingEntity): Result<com.swimgym.app.data.model.BookingResponse> = withContext(Dispatchers.IO) {
        try {
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

            val response = apiClient.postBooking(
                "https://swimgym.virtuagym.com/classes/class/${training.id}?event_type=8",
                formBody
            )

            if (response.isSuccessful || response.code == 302) {
                Result.success(
                    com.swimgym.app.data.model.BookingResponse(
                        id = 0,
                        trainingId = training.id,
                        status = "confirmed",
                        className = training.title
                    )
                )
            } else {
                Result.failure(Exception("Booking failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getNextTraining(
        startTime: Long,
        title: String,
        trainings: List<TrainingEntity>
    ): TrainingEntity? {
        val calendar = java.util.Calendar.getInstance()
        val now = calendar.timeInMillis / 1000
        var nexttime = startTime
        val week = 7 * 86400
        while (nexttime < now) {
            nexttime += week
        }
        if (nexttime > now + week) {
            return null
        }
        if (nexttime < now + 86400) {
            return null
        }

        for (training in trainings) {
            if (training.startTime == nexttime) {
                if (training.title.equals(title, ignoreCase = true)) {
                    return training
                } else {
                    // warn user training is renamed
                }
            }
        }
        return null
    }

    private fun shouldBookForAlarm(trainingStart: Long, now: Long): Boolean {
        return trainingStart < now + 7 * 86400 && trainingStart > now + 2 * 86400
    }
}
