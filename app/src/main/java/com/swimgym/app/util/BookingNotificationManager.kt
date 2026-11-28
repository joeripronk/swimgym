package com.swimgym.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.swimgym.app.MainActivity
import com.swimgym.app.R
import com.swimgym.app.data.repository.RetryFailureReason
import com.swimgym.app.receiver.ScheduledBookingReceiver
import java.text.SimpleDateFormat
import java.util.Locale

class BookingNotificationManager(
    private val context: Context
) {
    companion object {
        private const val BOOKING_CHANNEL_ID = "swimgym_booking_channel"
        private const val BOOKING_CHANNEL_NAME = "Booking Confirmations"
        private const val BOOKING_CHANNEL_DESCRIPTION = "Notifications for training booking confirmations"
        private const val SYNC_CHANNEL_ID = "swimgym_notifications"
        private const val SYNC_CHANNEL_NAME = "Schedule Sync"
        private const val SYNC_CHANNEL_DESCRIPTION = "Results of the daily schedule sync"

        const val NOTIFICATION_ID_BASE = 1000
        const val LOGIN_REQUIRED_ID = 9999
        const val SYNC_ID = 5000
        const val BOOKING_RETRY_ID = 20000
        const val CALENDAR_CONFLICT_ID = 4001
        const val BOOKING_NOT_AVAILABLE_ID = 4300

        /** Single source of truth for both channels, shared with app startup. */
        fun createNotificationChannels(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    BOOKING_CHANNEL_ID,
                    BOOKING_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = BOOKING_CHANNEL_DESCRIPTION
                    enableVibration(false)
                }
            )
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    SYNC_CHANNEL_ID,
                    SYNC_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = SYNC_CHANNEL_DESCRIPTION
                }
            )
        }
    }

    private fun notificationManagerOrNull(): NotificationManager? {
        if (!hasNotificationPermission(context)) return null
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels(context)
        return notificationManager
    }

    private fun mainPendingIntent(
        requestCode: Int = 0,
        flags: Int = Intent.FLAG_ACTIVITY_SINGLE_TOP,
        action: String? = null
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.flags = flags
            if (action != null) setAction(action)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showNotification(
        notificationManager: NotificationManager,
        notificationId: Int,
        channelId: String,
        title: String,
        content: String,
        priority: Int,
        pendingIntent: PendingIntent,
        category: String? = null,
        actions: List<Pair<String, PendingIntent>> = emptyList()
    ) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        if (category != null) builder.setCategory(category)
        actions.forEach { (label, actionIntent) ->
            builder.addAction(0, label, actionIntent)
        }
        notificationManager.notify(notificationId, builder.build())
    }

    fun showBookingConfirmation(
        trainingName: String,
        classTime: String,
        classDate: String,
        instructor: String,
        isScheduledBooking: Boolean = false,
        location: String = "",
        spotsAvailable: Int = 0,
        bookingCount: Int = 0,
        maxRepeatCount: Int? = null
    ) {
        val notificationManager = notificationManagerOrNull() ?: return

        val title = if (isScheduledBooking) {
            "$trainingName $instructor $classTime Booked (recurring) ✓"
        } else {
            "$trainingName $instructor $classTime Booked ✓"
        }

        val bookingProgress = if (isScheduledBooking && maxRepeatCount != null) {
            "Booking $bookingCount of $maxRepeatCount"
        } else {
            ""
        }

        val trainingDetails = formatTrainingDetails(trainingName, classTime, classDate, instructor, location, spotsAvailable)
        val content = if (isScheduledBooking) {
            "$trainingDetails\n\n$bookingProgress"
        } else {
            trainingDetails
        }

        showNotification(
            notificationManager,
            NOTIFICATION_ID_BASE,
            BOOKING_CHANNEL_ID,
            title,
            content,
            NotificationCompat.PRIORITY_HIGH,
            mainPendingIntent()
        )
    }

    fun showBookingRetry(
        bookingId: Long,
        trainingName: String,
        classDate: String,
        classTime: String,
        instructor: String,
        reason: RetryFailureReason,
        retryCount: Int,
        maxRetries: Int
    ) {
        val notificationManager = notificationManagerOrNull() ?: return

        val title = "Booking Failed - Retry Available"
        val trainingDetails = formatTrainingDetails(trainingName, classTime, classDate, instructor)
        val content = "Retrying \"$trainingName\" (${reason.displayName}, attempt $retryCount of $maxRetries). " +
            "Tap to retry now or wait for the automatic retry in 15 minutes.\n\n$trainingDetails"

        val retryIntent = Intent(context, ScheduledBookingReceiver::class.java).apply {
            action = ScheduledBookingReceiver.ACTION_RETRY_BOOKING
            putExtra("booking_id", bookingId)
        }

        val retryPendingIntent = PendingIntent.getBroadcast(
            context,
            bookingId.toInt(),
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, ScheduledBookingReceiver::class.java).apply {
            action = ScheduledBookingReceiver.ACTION_STOP_RETRY_BOOKING
            putExtra("booking_id", bookingId)
        }

        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            bookingId.toInt() + 1_000_000,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        showNotification(
            notificationManager,
            retryNotificationId(bookingId),
            BOOKING_CHANNEL_ID,
            title,
            content,
            NotificationCompat.PRIORITY_HIGH,
            mainPendingIntent(),
            actions = listOf(
                "Retry Now" to retryPendingIntent,
                "Stop retrying" to stopPendingIntent
            )
        )
    }

    fun showRetriesStopped(
        bookingId: Long,
        trainingName: String,
        reason: RetryFailureReason,
        retryCount: Int
    ) {
        val notificationManager = notificationManagerOrNull() ?: return

        val content = "Automatic retries for \"$trainingName\" stopped after $retryCount failed " +
            "attempts (last reason: ${reason.displayName}). Open My Bookings to resume."

        showNotification(
            notificationManager,
            retryNotificationId(bookingId),
            BOOKING_CHANNEL_ID,
            "Booking retries stopped",
            content,
            NotificationCompat.PRIORITY_HIGH,
            mainPendingIntent(bookingId.toInt())
        )
    }

    fun dismissRetryNotification(bookingId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(retryNotificationId(bookingId))
    }

    private fun retryNotificationId(bookingId: Long): Int =
        // Keep the full 14-bit slot range so sequential ids do not collide
        // after only a thousand bookings.
        BOOKING_RETRY_ID + (bookingId % 16384).toInt()

    fun showTrainingRenamed(oldName: String, newName: String) {
        val notificationManager = notificationManagerOrNull() ?: return

        val title = "Training Renamed"
        val content = "Your scheduled training was renamed:\n\n$oldName → $newName"

        showNotification(
            notificationManager,
            3000,
            BOOKING_CHANNEL_ID,
            title,
            content,
            NotificationCompat.PRIORITY_HIGH,
            mainPendingIntent()
        )
    }

    fun showScheduledBookingConflictWarning(
        bookingId: Long,
        trainingName: String,
        classDate: String,
        classTime: String,
        instructor: String,
        conflictTitle: String
    ) {
        val notificationManager = notificationManagerOrNull() ?: return

        // Deterministic per-booking id; hashCode() only hashed the low bits of
        // sequential ids and let different bookings overwrite each other.
        val notificationId = CALENDAR_CONFLICT_ID + (bookingId % 200).toInt()

        val title = "Calendar Conflict - Recurring Booking"
        val trainingDetails = formatTrainingDetails(trainingName, classTime, classDate, instructor)
        val content = "Your recurring booking more than a week ahead conflicts with \"$conflictTitle\" on your calendar. Review your calendar, the booking will be made automatically.\n\n$trainingDetails"

        showNotification(
            notificationManager,
            notificationId,
            BOOKING_CHANNEL_ID,
            title,
            content,
            NotificationCompat.PRIORITY_HIGH,
            mainPendingIntent(notificationId)
        )
    }

    fun showScheduledBookingNotAvailable(
        bookingId: Long,
        trainingName: String,
        reason: String
    ) {
        val notificationManager = notificationManagerOrNull() ?: return

        val notificationId = BOOKING_NOT_AVAILABLE_ID + (bookingId % 200).toInt()

        val title = "Recurring Booking Not Available"
        val content = "Your recurring booking \"$trainingName\" more than a week ahead cannot be booked: $reason"

        showNotification(
            notificationManager,
            notificationId,
            BOOKING_CHANNEL_ID,
            title,
            content,
            NotificationCompat.PRIORITY_HIGH,
            mainPendingIntent(notificationId)
        )
    }

    fun showSyncNotification(success: Boolean) {
        val notificationManager = notificationManagerOrNull() ?: return

        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(System.currentTimeMillis())
        val title = if (success) "Schedule synced ✓" else "Schedule sync failed"
        val content = if (success) {
            "Your schedule has been updated ($time)"
        } else {
            "SwimGym could not sync your schedule ($time). It will retry in 12 hours."
        }

        showNotification(
            notificationManager,
            SYNC_ID,
            SYNC_CHANNEL_ID,
            title,
            content,
            NotificationCompat.PRIORITY_LOW,
            mainPendingIntent(),
            category = NotificationCompat.CATEGORY_STATUS
        )
    }

    private fun formatTrainingDetails(
        trainingName: String,
        classTime: String,
        classDate: String,
        instructor: String,
        location: String = "",
        spotsAvailable: Int = 0
    ): String {
        val formattedDate = if (classDate.isNotEmpty()) {
            try {
                val inputFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val outputFormat = SimpleDateFormat("EEEE", Locale.getDefault())
                val date = inputFormat.parse(classDate)
                if (date != null) {
                    outputFormat.format(date)
                } else {
                    classDate
                }
            } catch (e: Exception) {
                classDate
            }
        } else {
            ""
        }

        val startTime = if (classTime.isNotEmpty()) {
            val timeParts = classTime.split(Regex("[-–—]")).map { it.trim() }
            if (timeParts.size >= 2) timeParts[0] else ""
        } else {
            ""
        }

        val lines = mutableListOf<String>()
        lines.add(trainingName)
        lines.add("$formattedDate $startTime")
        if (instructor.isNotEmpty()) lines.add(instructor)
        if (location.isNotEmpty()) lines.add(location)
        if (spotsAvailable > 0) lines.add("$spotsAvailable spots available")
        return lines.joinToString("\n")
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return PermissionHelper.hasNotificationPermission(context)
    }

    fun showLoginRequired() {
        val notificationManager = notificationManagerOrNull() ?: return

        val pendingIntent = mainPendingIntent(
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            action = com.swimgym.app.receiver.LoginActivity.ACTION_LOGIN_REQUIRED
        )

        showNotification(
            notificationManager,
            LOGIN_REQUIRED_ID,
            BOOKING_CHANNEL_ID,
            "Login Required",
            "Please log in to continue using SwimGym",
            NotificationCompat.PRIORITY_HIGH,
            pendingIntent,
            category = NotificationCompat.CATEGORY_REMINDER
        )
    }
}
