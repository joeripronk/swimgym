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
    private val webScraper = container.webScraper
    private val sessionRepository = SessionRepository(appContext)
    private var foregroundEnabled = false

    override fun doWork(): Result {
        return try {
            runBlocking {
                foregroundEnabled = sessionRepository.getForegroundServiceEnabled()
                webScraper.checkBookings()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    override fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            1,
            NotificationCompat.Builder(appContext, "swimgym_notifications")
                .setContentTitle("Checking Bookings")
                .setContentText("Automatically checking for available trainings")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(true)
                .build(),
            1
        )
    }
    companion object {
        const val WORK_NAME = "scheduled_booking_check"
    }
}