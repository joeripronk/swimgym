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
            val training = getNextTraining(adjustedStartTime, booking.className, trainings) ?: return@forEach
            val res = getTrainingDetails(training)
            if (!res.isSuccess) {
                showRetryNotification(booking)
                rescheduleAlarmForRetry(booking)
                return@forEach
            }
            val updtraining = res.getOrThrow()
            if (updtraining.isJoined) {
                scheduledBookingRepo.incrementBookingCount(booking.id, training)
                val maxRepeat = booking.maxRepeatCount
                if (maxRepeat != null && booking.bookedCount + 1 >= maxRepeat) {
                    scheduledBookingRepo.completeBooking(booking.id)
                }
                return@forEach
            }
            if (updtraining.isFull) {
                return@forEach
            }
            val bookResult = apiClient.bookTraining(training)
            bookResult.fold(
                onSuccess = {
                    scheduledBookingRepo.incrementBookingCount(booking.id, training)
                    val nextBooking = booking.copy(startTime = adjustedStartTime + 7 * 86400)
                    scheduledBookingRepo.saveBooking(nextBooking)
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

        val training = getNextTraining(adjustedStartTime, booking.className, trainings) ?: return@withContext false
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
            }
            return@withContext true
        }

        if (updtraining.isFull) {
            return@withContext false
        }

        val bookResult = apiClient.bookTraining(training)
        bookResult.fold(
            onSuccess = {
                scheduledBookingRepo.incrementBookingCount(bookingId, training)

                val nextBooking = updatedBooking.copy(startTime = updatedBooking.startTime + 7 * 86400)
                scheduledBookingRepo.saveBooking(nextBooking)

                val maxRepeat = booking.maxRepeatCount
                if (maxRepeat != null && booking.bookedCount + 1 >= maxRepeat) {
                    scheduledBookingRepo.completeBooking(bookingId)
                } else {
                    alarmScheduler.scheduleBooking(bookingId,nextBooking.startTime * 1000)
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

}
