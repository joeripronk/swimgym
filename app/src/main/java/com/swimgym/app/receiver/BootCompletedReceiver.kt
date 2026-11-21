package com.swimgym.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.swimgym.app.di.SwimGymAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootCompletedReceiver"
        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return
        Log.d(TAG, "Boot completed, re-arming alarms")

        val container = SwimGymAppContainer.getInstance()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                container.alarmScheduler.scheduleSyncAlarm()
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
