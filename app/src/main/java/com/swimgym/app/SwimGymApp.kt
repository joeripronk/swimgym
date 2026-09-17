package com.swimgym.app

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import com.swimgym.app.di.SwimGymAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SwimGymApp : Application() {
    
    companion object {
        lateinit var instance: SwimGymApp
            private set
    }

    private val ioScope = CoroutineScope(Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        SwimGymAppContainer.getInstance()
        
        // Create notification channel for foreground service
        createNotificationChannel()

        // Arm the schedule sync alarm
        SwimGymAppContainer.getInstance().alarmScheduler.scheduleSyncAlarm()

        // Reschedule booking alarms on app start
        ioScope.launch {
            val bookings = SwimGymAppContainer.getInstance().scheduledBookingRepository.getAllBookings()
            SwimGymAppContainer.getInstance().alarmScheduler.rescheduleBookingAlarms(bookings)
        }

        // Register lifecycle callback to reschedule alarms on resume
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                ioScope.launch {
                    val bookings = SwimGymAppContainer.getInstance().scheduledBookingRepository.getAllBookings()
                    SwimGymAppContainer.getInstance().alarmScheduler.rescheduleBookingAlarms(bookings)
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityDestroyed(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityStopped(activity: Activity) {}
        })
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