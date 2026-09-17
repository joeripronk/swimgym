package com.swimgym.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.swimgym.app.di.SwimGymAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ScheduleSyncReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ScheduleSyncReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm fired, syncing schedule")
        val pendingResult = goAsync()
        val container = SwimGymAppContainer.getInstance()

        GlobalScope.launch(Dispatchers.IO) {
            var success = false
            try {
                success = container.trainingRepository.refreshSchedule().isSuccess
                val notificationsEnabled = container.sessionRepository.getAlarmNotificationEnabled()
                if (notificationsEnabled) {
                    container.bookingNotificationManager.showSyncNotification(success)
                }
                container.alarmScheduler.scheduleSyncAlarm()
            } catch (e: Exception) {
                Log.e(TAG, "Schedule sync failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
