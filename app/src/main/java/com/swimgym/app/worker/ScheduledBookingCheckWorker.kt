package com.swimgym.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swimgym.app.data.api.WebScraper
import com.swimgym.app.data.repository.ScheduledBookingRepository
import com.swimgym.app.data.repository.ScheduledBookingStatus
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
            val activeBookings = bookings.filter { it.status == ScheduledBookingStatus.ACTIVE }

            activeBookings.forEach { booking ->
                processBooking(booking)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun processBooking(booking: com.swimgym.app.data.repository.ScheduledBooking) {
        try {
            val shouldBook = shouldBookNow(booking)
            if (!shouldBook) return

            val result = webScraper.bookTraining(
                trainingId = booking.trainingId,
                className = booking.className,
                classTime = booking.classTime,
                classDate = calculateNextDate(booking)
            )

            result.fold(
                onSuccess = {
                    scheduledBookingRepo.incrementBookingCount(booking.id)

                    val maxRepeat = booking.maxRepeatCount
                    if (maxRepeat != null && booking.bookedCount + 1 >= maxRepeat) {
                        scheduledBookingRepo.completeBooking(booking.id)
                        return
                    }

                    val nextTraining = webScraper.findNextTrainingByTitle(
                        title = booking.className,
                        weekday = booking.classDate,
                        startTime = booking.classTime
                    )

                    nextTraining.fold(
                        onSuccess = { training ->
                            if (training != null) {
                                scheduledBookingRepo.updateTrainingId(booking.id, training.id)
                            }
                        },
                        onFailure = {
                            // No next training found, keep current training ID
                        }
                    )

                    // Show notification for successful scheduled booking
                    notificationManager.showBookingConfirmation(
                        trainingName = booking.className,
                        classTime = booking.classTime,
                        classDate = booking.classDate,
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
        val currentDate = calendar.time

        calendar.timeInMillis = booking.startTime * 1000
        calendar.add(Calendar.DAY_OF_YEAR, -7 )
        val nextBookingDate = calendar.time

        return !nextBookingDate.after(currentDate)
    }

    private fun calculateNextDate(booking: com.swimgym.app.data.repository.ScheduledBooking): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = booking.createdAt
        calendar.add(Calendar.DAY_OF_YEAR, 7 * (booking.bookedCount + 1))

        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }

    companion object {
        const val WORK_NAME = "scheduled_booking_check"
    }
}
