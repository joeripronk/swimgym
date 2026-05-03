package com.swimgym.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.swimgym.app.data.api.WebScraper
import com.swimgym.app.worker.ScheduledBookingCheckWorker
import com.swimgym.app.worker.ScheduleSyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SwimGymApp : Application() {

    @Inject
    lateinit var webScraper: WebScraper

    override fun onCreate() {
        super.onCreate()

        // Load cached cookies on app startup
        CoroutineScope(Dispatchers.IO).launch {
            webScraper.loadCookiesFromCache()
        }

        // Schedule periodic booking check every 30 minutes
        val bookingCheckRequest = PeriodicWorkRequestBuilder<ScheduledBookingCheckWorker>(
            1, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ScheduledBookingCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            bookingCheckRequest
        )

        // Schedule periodic schedule sync every 30 minutes
        val scheduleSyncRequest = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(
            30, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ScheduleSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            scheduleSyncRequest
        )
    }
}