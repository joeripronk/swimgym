package com.swimgym.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.swimgym.app.worker.ScheduledBookingCheckWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class SwimGymApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Schedule periodic booking check every 30 minutes
        val periodicWorkRequest = PeriodicWorkRequestBuilder<ScheduledBookingCheckWorker>(
            30, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ScheduledBookingCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }
}