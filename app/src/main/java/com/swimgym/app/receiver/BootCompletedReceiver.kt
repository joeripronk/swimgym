package com.swimgym.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.swimgym.app.di.SwimGymAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "Boot completed, re-arming alarms")

        val container = SwimGymAppContainer.getInstance()
        container.alarmScheduler.scheduleSyncAlarm()

        val pendingResult = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val bookings = container.scheduledBookingRepository.getAllBookings()
                container.alarmScheduler.rescheduleBookingAlarms(bookings)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule booking alarms", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
