package com.swimgym.app

import android.app.Application
import com.swimgym.app.di.SwimGymAppContainer
import com.swimgym.app.util.BookingNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        
        // Channels are defined once in BookingNotificationManager so startup
        // cannot overwrite them with conflicting name/description metadata.
        BookingNotificationManager.createNotificationChannels(this)

        // Arm the schedule sync alarm and any active booking alarms asynchronously
        // so the main thread is not blocked during cold start.
        CoroutineScope(Dispatchers.IO).launch {
            SwimGymAppContainer.getInstance().alarmScheduler.scheduleSyncAlarm()
            val bookings = SwimGymAppContainer.getInstance().scheduledBookingRepository.getAllBookings()
            SwimGymAppContainer.getInstance().alarmScheduler.rescheduleBookingAlarms(bookings)
        }
    }
    
    // onTrimMemory is intentionally not overridden: alarms live in system_server,
    // so cancelling them frees no memory here, and cancelling the sync alarm would
    // break the daily 09:00/21:00 chain for a running app. The SIGILL in
    // libwebviewchromium.so was caused by allocating a WebView under memory
    // pressure, which this override used to do.
    
    fun getContainer(): SwimGymAppContainer = SwimGymAppContainer.getInstance()
}