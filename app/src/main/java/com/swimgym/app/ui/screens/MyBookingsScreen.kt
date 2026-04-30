package com.swimgym.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swimgym.app.data.repository.ScheduledBooking
import com.swimgym.app.data.repository.ScheduledBookingStatus
import com.swimgym.app.domain.model.Booking
import com.swimgym.app.domain.model.BookingStatus
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(
    bookings: List<Booking>,
    scheduledBookings: List<ScheduledBooking>,
    onBack: () -> Unit,
    onCancelBooking: (String) -> Unit,
    onPauseScheduled: (String) -> Unit,
    onResumeScheduled: (String) -> Unit,
    onDeleteScheduled: (String) -> Unit,
    onScheduledBookingClick: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Bookings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (bookings.isEmpty() && scheduledBookings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No bookings yet")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (scheduledBookings.isNotEmpty()) {
                    item {
                        Text(
                            text = "Scheduled Bookings",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(scheduledBookings) { scheduled ->
                        ScheduledBookingCard(
                            scheduled = scheduled,
                            onClick = { onScheduledBookingClick(scheduled.trainingId) },
                            onPause = { onPauseScheduled(scheduled.id) },
                            onResume = { onResumeScheduled(scheduled.id) },
                            onDelete = { onDeleteScheduled(scheduled.id) }
                        )
                    }
                }

                if (bookings.isNotEmpty()) {
                    item {
                        Text(
                            text = "Confirmed Bookings",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(bookings) { booking ->
                        BookingCard(
                            booking = booking,
                            onCancel = { onCancelBooking(booking.trainingId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingCard(
    booking: Booking,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            ,
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Training #${booking.trainingId.substringBefore("-")}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Status: ${booking.status.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = when (booking.status) {
                    BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
                    BookingStatus.CANCELLED -> MaterialTheme.colorScheme.error
                    BookingStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (booking.status == BookingStatus.CONFIRMED) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onCancel) {
                    Text("Cancel Booking")
                }
            }
        }
    }
}

@Composable
private fun ScheduledBookingCard(
    scheduled: ScheduledBooking,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val weekday = if (scheduled.startTime > 0) {
        dateFormat.format(Date(scheduled.startTime))
    } else {
        ""
    }
    val startTimeStr = if (scheduled.startTime > 0) {
        timeFormat.format(Date(scheduled.startTime))
    } else {
        scheduled.classTime
    }

    val nextBookingTime = remember(scheduled.startTime, scheduled.bookedCount) {
        if (scheduled.startTime > 0) {
            calculateNextBookingTime(scheduled.startTime, scheduled.bookedCount)
        } else {
            null
        }
    }

    val timeUntilNext = remember(nextBookingTime) {
        if (nextBookingTime != null) {
            formatTimeUntilNext(nextBookingTime)
        } else {
            ""
            }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()

    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scheduled.className,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (scheduled.instructor.isNotEmpty()) {
                        Text(
                            text = "Trainer: ${scheduled.instructor}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (weekday.isNotEmpty()) {
                        Text(
                            text = "$weekday at $startTimeStr",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Status: ${scheduled.status.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (scheduled.status) {
                            ScheduledBookingStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                            ScheduledBookingStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                            ScheduledBookingStatus.COMPLETED -> MaterialTheme.colorScheme.outline
                            ScheduledBookingStatus.INVALIDATED -> MaterialTheme.colorScheme.error
                        }
                    )
                    if (scheduled.status == ScheduledBookingStatus.ACTIVE && timeUntilNext.isNotEmpty()) {
                        Text(
                            text = "Next booking: $timeUntilNext",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    val progressText = if (scheduled.maxRepeatCount != null) {
                        "Progress: ${scheduled.bookedCount} / ${scheduled.maxRepeatCount}"
                    } else {
                        "Booked: ${scheduled.bookedCount} times"
                    }
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodySmall
                    )

                }

                Row {
                    if (scheduled.status == ScheduledBookingStatus.ACTIVE) {
                        TextButton(onClick = onPause) {
                            Text("Pause")
                        }
                    } else if (scheduled.status == ScheduledBookingStatus.PAUSED) {
                        TextButton(onClick = onResume) {
                            Text("Resume")
                        }
                    }
                    TextButton(onClick = onDelete) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

private fun calculateNextBookingTime(startTime: Long, bookedCount: Int): Long {
    val calendar = Calendar.getInstance()
    val originalDayOfWeek = calendar.apply { timeInMillis = startTime }.get(Calendar.DAY_OF_WEEK)
    val originalHour = calendar.get(Calendar.HOUR_OF_DAY)
    val originalMinute = calendar.get(Calendar.MINUTE)

    val now = System.currentTimeMillis()
    calendar.timeInMillis = now

    // Set to the target day and time
    calendar.set(Calendar.DAY_OF_WEEK, originalDayOfWeek)
    calendar.set(Calendar.HOUR_OF_DAY, originalHour)
    calendar.set(Calendar.MINUTE, originalMinute)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    // If this week's occurrence has passed, move to next week
    if (calendar.timeInMillis <= now) {
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
    }

    // Add weeks based on booked count (each booking is ~1 week apart)
    // The actual next run should be after the last successful booking
    // For simplicity, we calculate based on weekly recurrence
    return calendar.timeInMillis
}

private fun formatTimeUntilNext(nextTime: Long): String {
    val now = System.currentTimeMillis()
    val diff = nextTime - now

    if (diff <= 0) return "soon"

    val days = diff / (24 * 60 * 60 * 1000)
    val hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
    val minutes = (diff % (60 * 60 * 1000)) / (60 * 1000)

    return when {
        days > 0 -> "in ${days}d ${String.format("%02d:%02d", hours, minutes)}"
        hours > 0 -> "in ${hours}h ${String.format("%02d", minutes)}m"
        else -> "in ${minutes}m"
    }
}