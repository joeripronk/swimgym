package com.swimgym.app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.swimgym.app.R
import com.swimgym.app.data.repository.ScheduledBookingStatus
import com.swimgym.app.di.SwimGymAppContainer
import com.swimgym.app.util.AlarmScheduler
import com.swimgym.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SyncAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val result = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val container = SwimGymAppContainer.getInstance()
                val bookings = container.scheduledBookingRepository.getAllBookings()
                val hasActiveBooking =
                    bookings.any { it.status == ScheduledBookingStatus.ACTIVE }

                if (hasActiveBooking) {
                    container.webScraper.checkBookings()
                    notifySuccess(appContext, "Scheduled booking check completed")
                } else {
                    container.webScraper.getSchedule()
                    notifySuccess(appContext, "Schedule refreshed")
                }
            } catch (_: Exception) {
                // Work failed; the alarm will be re-armed below and retried on the next tick.
            } finally {
                try {
                    AlarmScheduler.scheduleNext(appContext)
                } catch (_: Exception) {
                    // Ignore re-arm failure; periodic WorkManager remains a fallback.
                }
                result.finish()
            }
        }
    }

    private fun notifySuccess(context: Context, detail: String) {
        if (!PermissionHelper.hasNotificationPermission(context)) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(manager)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("SwimGym sync")
            .setContentText(detail)
            .setShowWhen(true)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scheduled Sync",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when the scheduled sync alarm fires"
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_SYNC = "com.swimgym.app.SYNC_ALARM"
        private const val CHANNEL_ID = "swimgym_sync_channel"
        private const val NOTIFICATION_ID = 2000
    }
}
