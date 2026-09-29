package com.swimgym.app

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.webkit.CookieManager
import android.webkit.WebView
import com.swimgym.app.di.SwimGymAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SwimGymApp : Application() {
    
    companion object {
        lateinit var instance: SwimGymApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        SwimGymAppContainer.resetOnAppRestart()
        SwimGymAppContainer.getInstance()
        
        // Create notification channel for foreground service
        createNotificationChannel()

        // Arm the schedule sync alarm
        CoroutineScope(Dispatchers.IO).launch {
            SwimGymAppContainer.getInstance().alarmScheduler.scheduleSyncAlarm()
        }

        // Reschedule booking alarms on app start
        runBlocking(Dispatchers.IO) {
            val bookings = SwimGymAppContainer.getInstance().scheduledBookingRepository.getAllBookings()
            SwimGymAppContainer.getInstance().alarmScheduler.rescheduleBookingAlarms(bookings)
        }
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Clear WebView cookies and cache to prevent native state corruption
        // on process restart (fixes SIGILL crash in libwebviewchromium.so)
        if (level >= TRIM_MEMORY_MODERATE) {
            CookieManager.getInstance().removeAllCookies(null)
            WebView(applicationContext).clearCache(true)
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

    fun getContainer(): SwimGymAppContainer = SwimGymAppContainer.getInstance()
}