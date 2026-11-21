package com.swimgym.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.swimgym.app.di.SwimGymAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScheduleSyncReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ScheduleSyncReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm fired, syncing schedule")
        val pendingResult = goAsync()
        val container = SwimGymAppContainer.getInstance()

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            // Re-arm before doing any network work. The alarm is one-shot; if the
            // process is killed during the fetch, the chain must already be intact.
            try {
                container.alarmScheduler.scheduleSyncAlarm()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-arm sync alarm", e)
            }
            var success = false
            try {
                success = container.trainingRepository.refreshSchedule().isSuccess
                val notificationsEnabled = container.sessionRepository.getAlarmNotificationEnabled()
                if (notificationsEnabled) {
                    container.bookingNotificationManager.showSyncNotification(success)
                }
                if (container.sessionRepository.isLoggedIn.first()) {
                    container.bookingScheduler.checkCalendarConflicts()
                    // The booking alarm is a one-shot armed for the exact moment the
                    // window opens. If it was missed (device off, Doze, clock change),
                    // the daily sync is the safety net that still books the class.
                    try {
                        container.bookingScheduler.checkBookings()
                    } catch (e: Exception) {
                        Log.e(TAG, "Scheduled booking check failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Schedule sync failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
