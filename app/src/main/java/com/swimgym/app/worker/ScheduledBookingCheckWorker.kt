package com.swimgym.app.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.swimgym.app.di.SwimGymAppContainer
import kotlinx.coroutines.runBlocking

class ScheduledBookingCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    private val container = SwimGymAppContainer.getInstance()
    private val webScraper = container.webScraper

    override fun doWork(): Result {
        return try {
            runBlocking {
                webScraper.checkBookings()
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
    companion object {
        const val WORK_NAME = "scheduled_booking_check"
    }
}