package com.swimgym.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.swimgym.app.di.SwimGymAppContainer
import kotlinx.coroutines.runBlocking

class ScheduleSyncReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ScheduleSyncReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm fired, syncing schedule")
        val container = SwimGymAppContainer.getInstance()

        var success = false
        try {
            success = runBlocking {
                val now = System.currentTimeMillis() / 1000
                container.dao.deleteOldTrainings(now)
                container.trainingRepository.refreshSchedule().isSuccess
            }
        } catch (e: Exception) {
            Log.e(TAG, "Schedule sync failed", e)
        }

        val notificationsEnabled = runBlocking {
            container.sessionRepository.getAlarmNotificationEnabled()
        }
        if (notificationsEnabled) {
            container.bookingNotificationManager.showSyncNotification(success)
        }

        container.alarmScheduler.scheduleSyncAlarm()
    }
}
