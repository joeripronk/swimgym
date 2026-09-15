package com.swimgym.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.swimgym.app.data.repository.ScheduledBookingStatus
import com.swimgym.app.di.SwimGymAppContainer
import com.swimgym.app.receiver.SyncAlarmReceiver

object AlarmScheduler {
    private const val MAX_INTERVAL_MS = 12 * 60 * 60 * 1000L
    private const val WEEK_SECONDS = 7 * 86400L
    private const val REQUEST_CODE = 1

    /**
     * Arms a single alarm to fire at the earlier of:
     *  - the next active scheduled booking, or
     *  - 12 hours from now.
     * Returns the epoch millis the alarm is set for.
     */
    suspend fun scheduleNext(context: Context): Long {
        val appContext = context.applicationContext
        val container = SwimGymAppContainer.getInstance()

        val bookings = container.scheduledBookingRepository.getAllBookings()
        val nowSec = System.currentTimeMillis() / 1000
        val nowMs = System.currentTimeMillis()

        var nextBookingMs: Long? = null
        for (booking in bookings) {
            if (booking.status != ScheduledBookingStatus.ACTIVE) continue
            var t = booking.startTime
            while (t < nowSec) t += WEEK_SECONDS
            val tMs = t * 1000L
            if (nextBookingMs == null || tMs < nextBookingMs) {
                nextBookingMs = tMs
            }
        }

        val fallback = nowMs + MAX_INTERVAL_MS
        val fireAt = nextBookingMs?.let { minOf(fallback, it) } ?: fallback

        val intent = Intent(appContext, SyncAlarmReceiver::class.java).apply {
            action = SyncAlarmReceiver.ACTION_SYNC
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pendingIntent)
        return fireAt
    }
}
