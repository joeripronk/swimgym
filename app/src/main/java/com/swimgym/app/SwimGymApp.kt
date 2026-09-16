package com.swimgym.app

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import com.swimgym.app.di.SwimGymAppContainer
import java.util.concurrent.TimeUnit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import com.swimgym.app.worker.ScheduledBookingCheckWorker
import com.swimgym.app.worker.ScheduleSyncWorker
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking

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

        // Arm the 12h schedule sync alarm
        SwimGymAppContainer.getInstance().alarmScheduler.scheduleSyncAlarm()

        // Reschedule booking alarms on app start
        rescheduleAllAlarms()

        // Register lifecycle callback to reschedule alarms on resume
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                rescheduleAllAlarms()
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityDestroyed(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityStopped(activity: Activity) {}
        })

        // Schedule periodic workers only if work manager is enabled in settings
        val workManagerEnabled = runBlocking {
            SwimGymAppContainer.getInstance().sessionRepository.getWorkManagerEnabled()
        }
        if (workManagerEnabled) {
            schedulePeriodicWorkers()
        }
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

    private fun schedulePeriodicWorkers() {
        val bookingCheckRequest = PeriodicWorkRequestBuilder<ScheduledBookingCheckWorker>(
            15, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ScheduledBookingCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            bookingCheckRequest
        )

        val scheduleSyncRequest = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(
            1440, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ScheduleSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            scheduleSyncRequest
        )
    }

    private fun rescheduleAllAlarms() {
        runBlocking {
            val bookings = SwimGymAppContainer.getInstance().scheduledBookingRepository.getAllBookings()
            SwimGymAppContainer.getInstance().alarmScheduler.rescheduleBookingAlarms(bookings)
        }
    }

    fun getContainer(): SwimGymAppContainer = SwimGymAppContainer.getInstance()
}