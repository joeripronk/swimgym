package com.swimgym.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.swimgym.app.data.repository.ScheduledBooking
import com.swimgym.app.data.repository.ScheduledBookingStatus
import com.swimgym.app.receiver.ScheduleSyncReceiver
import com.swimgym.app.receiver.ScheduledBookingReceiver

class AlarmScheduler(private val context: Context) {
    companion object {
        const val TAG = "AlarmScheduler"
        private const val SYNC_REQUEST_CODE = 0x7E57
        private const val BOOKING_WINDOW_AHEAD_SECONDS = 7 * 86400
        private const val BOOKING_LEAD_MILLIS = 5 * 60 * 1000L
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleBooking(bookingId: Long, alarmTimeMillis: Long) {
        if (alarmTimeMillis <= System.currentTimeMillis()) {
            fireImmediately(bookingId)
            return
        }

        val pendingIntent = createPendingIntent(bookingId)
        val exactSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (exactSupported) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTimeMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTimeMillis,
                pendingIntent
            )
        }
    }

    private fun nextSyncTimeMillis(): Long {
        val now = System.currentTimeMillis()
        for (hour in listOf(9, 21)) {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            if (cal.timeInMillis > now) return cal.timeInMillis
        }
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun cancelBooking(bookingId: Long) {
        val pendingIntent = createPendingIntent(bookingId)
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Arms a one-shot alarm at the next 09:00 or 21:00 that fires
     * ScheduleSyncReceiver. The receiver re-arms it at the following slot,
     * giving two daily syncs.
     */
    fun scheduleSyncAlarm() {
        val alarmTimeMillis = nextSyncTimeMillis()
        val pendingIntent = createSyncPendingIntent()
        val exactSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (exactSupported) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTimeMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTimeMillis,
                pendingIntent
            )
        }
        Log.d(TAG, "Sync alarm scheduled for $alarmTimeMillis")
    }

    /**
     * Re-arms the one-shot booking alarm for every ACTIVE scheduled booking.
     * The alarm fires 5 minutes after the booking window opens (7 days before
     * the class). Bookings whose window is already open fire immediately.
     */
    fun rescheduleBookingAlarms(bookings: List<ScheduledBooking>) {
        bookings
            .filter { it.status == ScheduledBookingStatus.ACTIVE }
            .forEach { booking ->
                val alarmTimeMillis = bookingAlarmTimeMillis(booking)
                Log.d(TAG, "Rescheduling booking alarm ${booking.id} for $alarmTimeMillis")
                scheduleBooking(booking.id, alarmTimeMillis)
            }
    }

    /**
     * Alarm time (epoch millis) for a booking: 5 minutes after its booking
     * window opens. If the stored start time is already in the past, the next
     * weekly occurrence is used.
     */
    fun bookingAlarmTimeMillis(booking: ScheduledBooking): Long {
        val nowSeconds = System.currentTimeMillis() / 1000
        var startSeconds = booking.startTime
        while (startSeconds < nowSeconds) {
            startSeconds += 7 * 86400
        }
        return (startSeconds - 7 * 86400 + 5 * 60) * 1000L
    }

    private fun createSyncPendingIntent(): PendingIntent {
        val intent = Intent(context, ScheduleSyncReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, SYNC_REQUEST_CODE, intent, flags)
    }

    private fun createPendingIntent(bookingId: Long): PendingIntent {
        val intent = Intent(context, ScheduledBookingReceiver::class.java).apply {
            putExtra("booking_id", bookingId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(
            context,
            bookingId.toInt(),
            intent,
            flags
        )
    }

    private fun fireImmediately(bookingId: Long) {
        val intent = Intent(context, ScheduledBookingReceiver::class.java).apply {
            putExtra("booking_id", bookingId)
        }
        context.sendBroadcast(intent)
    }
}
