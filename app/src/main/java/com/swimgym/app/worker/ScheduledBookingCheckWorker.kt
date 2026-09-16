package com.swimgym.app.worker

import android.content.Context
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.swimgym.app.di.SwimGymAppContainer
import com.swimgym.app.data.repository.SessionRepository
import kotlinx.coroutines.runBlocking
import androidx.core.app.NotificationCompat

class ScheduledBookingCheckWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    private val container = SwimGymAppContainer.getInstance()
    private val sessionRepository = SessionRepository(appContext)

    override fun doWork(): Result {
        return try {
            runBlocking { container.bookingScheduler.checkBookings() }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    override fun getForegroundInfo(): ForegroundInfo {
        val foregroundEnabled = runBlocking { sessionRepository.getForegroundServiceEnabled() }
        return ForegroundInfo(
            1,
            NotificationCompat.Builder(appContext, "swimgym_notifications")
                .setContentTitle("Checking Bookings")
                .setContentText("Automatically checking for available trainings")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(foregroundEnabled)
                .build(),
            1
        )
    }
    companion object {
        const val WORK_NAME = "scheduled_booking_check"
    }
}
