package com.swimgym.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.swimgym.app.MainActivity
import com.swimgym.app.R
import java.text.SimpleDateFormat
import java.util.Locale

class BookingNotificationManager(
    private val context: Context
) {
    companion object {
        private const val BOOKING_CHANNEL_ID = "swimgym_booking_channel"
        private const val BOOKING_CHANNEL_NAME = "Booking Confirmations"
        private const val BOOKING_CHANNEL_DESCRIPTION = "Notifications for training booking confirmations"
    const val NOTIFICATION_ID_BASE = 1000
    const val LOGIN_REQUIRED_ID = 9999
    const val SYNC_ID = 5000
    const val BOOKING_RETRY_ID = 2000
        private const val SYNC_CHANNEL_ID = "swimgym_notifications"
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
        if (!hasNotificationPermission(context)) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        val title = if (isScheduledBooking) {
            "$trainingName $instructor $classTime Booked (recurring) ✓"
        } else {
            "$trainingName $instructor $classTime Booked ✓"
        }

        val bookingProgress = if (isScheduledBooking && maxRepeatCount != null) {
            "Booking ${bookingCount + 1} of $maxRepeatCount"
        } else {
            ""
        }

        val trainingDetails = formatTrainingDetails(trainingName, classTime, classDate, instructor, location, spotsAvailable)
        val content = if (isScheduledBooking) {
            "$trainingDetails\n\n$bookingProgress"
        } else {
            trainingDetails
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, BOOKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(NOTIFICATION_ID_BASE, builder.build())
    }

    fun showBookingRetry(bookingId: Long, trainingName: String, classDate: String, classTime: String, instructor: String) {
        if (!hasNotificationPermission(context)) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        val title = "Booking Failed - Retry Available"
        val trainingDetails = formatTrainingDetails(trainingName, classTime, classDate, instructor)
        val content = "Your booking could not be completed. Tap to retry now or wait for automatic retry in 15 minutes.\n\n$trainingDetails"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val retryIntent = Intent(context, com.swimgym.app.receiver.ScheduledBookingReceiver::class.java).apply {
            setAction("com.swimgym.app.RETRY_BOOKING")
            putExtra("booking_id", bookingId)
        }

        val retryPendingIntent = PendingIntent.getBroadcast(
            context,
            bookingId.toInt(),
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, BOOKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Retry Now", retryPendingIntent)

        notificationManager.notify(BOOKING_RETRY_ID, builder.build())
    }

    fun showTrainingRenamed(oldName: String, newName: String) {
        if (!hasNotificationPermission(context)) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        val title = "Training Renamed"
        val content = "Your scheduled training was renamed:\n\n$oldName → $newName"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, BOOKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(3000, builder.build())
    }

    fun showSyncNotification(success: Boolean) {
        if (!hasNotificationPermission(context)) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(System.currentTimeMillis())
        val builder = NotificationCompat.Builder(context, SYNC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                if (success) "Schedule synced ✓" else "Schedule sync failed"
            )
            .setContentText(
                if (success) {
                    "Your schedule has been updated ($time)"
                } else {
                    "SwimGym could not sync your schedule ($time). It will retry in 12 hours."
                }
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        notificationManager.notify(SYNC_ID, builder.build())
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
            val timeParts = classTime.split("-").map { it.trim() }
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

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BOOKING_CHANNEL_ID,
                BOOKING_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = BOOKING_CHANNEL_DESCRIPTION
                enableVibration(false)
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return PermissionHelper.hasNotificationPermission(context)
    }

    fun showLoginRequired() {
        if (!hasNotificationPermission(context)) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            setAction(com.swimgym.app.receiver.LoginActivity.ACTION_LOGIN_REQUIRED)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, BOOKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Login Required")
            .setContentText("Please log in to continue using SwimGym")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        notificationManager.notify(LOGIN_REQUIRED_ID, builder.build())
    }
}
