package com.swimgym.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.swimgym.app.di.SwimGymAppContainer
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
        
        // Create notification channel for foreground service
        createNotificationChannel()

        // Schedule periodic booking check every 15 minutes
        val bookingCheckRequest = PeriodicWorkRequestBuilder<ScheduledBookingCheckWorker>(
            15, TimeUnit.MINUTES
        )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ScheduledBookingCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            bookingCheckRequest
        )

        // Schedule periodic schedule sync every 24 hours
        val scheduleSyncRequest = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(
            1440, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ScheduleSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            scheduleSyncRequest
        )
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "swimgym_notifications",
            "SwimGym Notifications",
            android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for booking checks"
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    fun getContainer(): SwimGymAppContainer = SwimGymAppContainer.getInstance()
}