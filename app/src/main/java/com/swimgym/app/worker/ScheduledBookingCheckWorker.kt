package com.swimgym.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swimgym.app.data.api.WebScraper
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.repository.ScheduledBookingRepository
import com.swimgym.app.data.repository.ScheduledBookingStatus
import com.swimgym.app.domain.model.Training
import com.swimgym.app.util.BookingNotificationManager

import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar

@HiltWorker
class ScheduledBookingCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val webScraper: WebScraper,
    private val scheduledBookingRepo: ScheduledBookingRepository,
    private val notificationManager: BookingNotificationManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val bookings = scheduledBookingRepo.getAllBookings()
            val trainings = webScraper.getSchedule()


            val activeBookings = bookings.filter { it.status == ScheduledBookingStatus.ACTIVE }

            activeBookings.forEach { booking ->
                processBooking(booking, trainings)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun getNextTraining(
        startTime: Long,
        title: String,
        trainings: List<TrainingEntity>
    ): TrainingEntity? {
        for (training in trainings) {
            val titleMatches = training.title.equals(title, ignoreCase = true)
            if (training.startTime == startTime + 7 * 86400000 && titleMatches)
                return training
        }
        return null
    }


    private suspend fun processBooking(booking: com.swimgym.app.data.repository.ScheduledBooking, trainings: List<TrainingEntity>) {
        try {
            val shouldBook = shouldBookNow(booking)
            if (!shouldBook) return
            val training = getNextTraining(booking.startTime, booking.className, trainings) ?: return
            val result = webScraper.bookTraining(training)

            result.fold(
                onSuccess = {
                    scheduledBookingRepo.incrementBookingCount(booking.id, training)

                    val maxRepeat = booking.maxRepeatCount
                    if (maxRepeat != null && booking.bookedCount + 1 >= maxRepeat) {
                        scheduledBookingRepo.completeBooking(booking.id)
                        return
                    }

                    notificationManager.showBookingConfirmation(
                        trainingName = training.title,
                        classTime = training.classTime,
                        classDate = training.classDate,
                        isScheduledBooking = true
                    )
                },
                onFailure = {
                    // Don't retry here, will be checked again in 30 minutes
                }
            )
        } catch (e: Exception) {
            // Error handled silently, will retry on next periodic run
        }
    }

    private fun shouldBookNow(booking: com.swimgym.app.data.repository.ScheduledBooking): Boolean {
        val calendar = Calendar.getInstance()
        return booking.startTime > calendar.time.time
    }


    companion object {
        const val WORK_NAME = "scheduled_booking_check"
    }
}
