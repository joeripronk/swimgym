package com.swimgym.app

import android.app.Application
import com.swimgym.app.di.SwimGymAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import com.swimgym.app.worker.ScheduledBookingCheckWorker
import com.swimgym.app.worker.ScheduleSyncWorker
import androidx.work.WorkManager

class SwimGymApp : Application() {
    
    companion object {
        lateinit var instance: SwimGymApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        SwimGymAppContainer.getInstance()

        // Schedule periodic booking check every 30 minutes
        val bookingCheckRequest = PeriodicWorkRequestBuilder<ScheduledBookingCheckWorker>(
            15, TimeUnit.MINUTES
        )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ScheduledBookingCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            bookingCheckRequest
        )

        // Schedule periodic schedule sync every 30 minutes
        val scheduleSyncRequest = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(
            1440, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ScheduleSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            scheduleSyncRequest
        )
    }

    fun getContainer(): SwimGymAppContainer = SwimGymAppContainer.getInstance()
}