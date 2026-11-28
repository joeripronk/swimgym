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
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.swimgym.app.data.repository.CalendarEventInfo
import com.swimgym.app.data.repository.DryRunResult
import com.swimgym.app.data.repository.ScheduledBooking
import com.swimgym.app.data.repository.ScheduledBookingStatus
import com.swimgym.app.di.SwimGymAppContainer
import com.swimgym.app.domain.model.Booking
import com.swimgym.app.domain.model.BookingStatus
import com.swimgym.app.util.addDaysToSeconds
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(
    bookings: List<Booking>,
    scheduledBookings: List<ScheduledBooking>,
    onBack: () -> Unit,
    onCancelBooking: (Booking) -> Unit,
    onPauseScheduled: (Long) -> Unit,
    onResumeScheduled: (Long) -> Unit,
    onDeleteScheduled: (Long) -> Unit,
    onStopRetrying: (Long) -> Unit,
    onScheduledBookingClick: (String) -> Unit,
    onBookingClick: (Booking) -> Unit
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
                            onClick = onScheduledBookingClick,
                            onPause = { onPauseScheduled(scheduled.id) },
                            onResume = { onResumeScheduled(scheduled.id) },
                            onDelete = { onDeleteScheduled(scheduled.id) },
                            onStopRetrying = { onStopRetrying(scheduled.id) }
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
                            onCancel = { onCancelBooking(booking) },
                            onClick = { onBookingClick(booking) }
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
    onCancel: () -> Unit,
    onClick: () -> Unit
) {
    val dayFormat = remember { SimpleDateFormat("EEE HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${dayFormat.format(Date(booking.startTime*1000))} ${booking.className}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Status: ${booking.status.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (booking.status) {
                        BookingStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
                        BookingStatus.CANCELLED -> MaterialTheme.colorScheme.error
                        BookingStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                        BookingStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
                        BookingStatus.BOOKED -> MaterialTheme.colorScheme.primary
                        BookingStatus.FAILED -> MaterialTheme.colorScheme.error
                    }
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${dateFormat.format(Date(booking.startTime*1000))} ${timeFormat.format(Date(booking.startTime*1000))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                if (booking.instructor.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Instructor: ${booking.instructor}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (booking.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = booking.imageUrl,
                    contentDescription = "Instructor image",
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun ScheduledBookingCard(
    scheduled: ScheduledBooking,
    onClick: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onStopRetrying: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var dryRunResult by remember { mutableStateOf<DryRunResult?>(null) }

    LaunchedEffect(scheduled.id, scheduled.startTime) {
        dryRunResult = SwimGymAppContainer.getInstance().bookingScheduler.simulateBooking(scheduled)
    }

    // The dry run resolves the exact cached training that will be booked next. The
    // stored trainingId points at last week's class, so never navigate with it when
    // a concrete next training is available.
    val nextTraining = when (val result = dryRunResult) {
        is DryRunResult.Ok -> result.training
        is DryRunResult.Conflict -> result.training
        else -> null
    }
    val displayStartTime = nextTraining?.startTime ?: scheduled.startTime
    val displayTitle = nextTraining?.title ?: scheduled.className
    val displayInstructor = nextTraining?.instructor?.takeIf { it.isNotBlank() }
        ?: scheduled.instructor

    val weekday = if (displayStartTime > 0) {
        dateFormat.format(Date(displayStartTime * 1000))
    } else {
        ""
    }
    val startTimeStr = if (displayStartTime > 0) {
        timeFormat.format(Date(displayStartTime * 1000))
    } else {
        "invalid"
    }
    val timeUntilNext = if (displayStartTime > 0) {
        formatTimeUntilNext(displayStartTime)
    } else {
        ""
    }
    val conflictEvents = (dryRunResult as? DryRunResult.Conflict)
        ?.conflicts
        ?.takeIf { displayStartTime > System.currentTimeMillis() / 1000 + 7 * 86400 }
        ?: emptyList()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(nextTraining?.id ?: scheduled.trainingId) }
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
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (displayInstructor.isNotEmpty()) {
                        Text(
                            text = "Trainer: $displayInstructor",
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
                        color = when {
                            scheduled.status == ScheduledBookingStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                            scheduled.status == ScheduledBookingStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                            scheduled.status == ScheduledBookingStatus.COMPLETED -> MaterialTheme.colorScheme.outline
                            scheduled.status == ScheduledBookingStatus.INVALIDATED -> MaterialTheme.colorScheme.error
                            scheduled.status == ScheduledBookingStatus.STOPPED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (scheduled.retryCount > 0 && scheduled.lastFailureReason != null) {
                        val summary = if (scheduled.status == ScheduledBookingStatus.STOPPED) {
                            "Retries stopped after ${scheduled.retryCount} attempts: ${scheduled.lastFailureReason.displayName}"
                        } else {
                            "Retry attempt ${scheduled.retryCount}: ${scheduled.lastFailureReason.displayName}"
                        }
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (scheduled.status == ScheduledBookingStatus.ACTIVE) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    when (val dryRun = dryRunResult) {
                        is DryRunResult.Ok -> {
                            Text(
                                text = "Dry-run: ready to book",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is DryRunResult.Renamed -> {
                            Text(
                                text = "Dry-run: training renamed to ${dryRun.newName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        is DryRunResult.NotAvailable -> {
                            Text(
                                text = "Dry-run: not available (${dryRun.reason})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        is DryRunResult.Conflict -> {
                            if (conflictEvents.isNotEmpty()) {
                                Text(
                                    text = "Dry-run: calendar conflict (${dryRun.conflictTitle})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        null -> Unit
                    }
                    if (conflictEvents.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "⚠ Calendar conflicts detected",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                conflictEvents.forEach { event ->
                                    val conflictTime = if (event.startMillis > 0) {
                                        "${timeFormat.format(Date(event.startMillis))} - ${timeFormat.format(Date(event.endMillis))}"
                                    } else {
                                        "all-day"
                                    }
                                    Text(
                                        text = "• ${event.title} ($conflictTime)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
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

                Column(horizontalAlignment = Alignment.End) {
                    if (!nextTraining?.imageUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = nextTraining.imageUrl,
                            contentDescription = "Instructor image",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (scheduled.status == ScheduledBookingStatus.ACTIVE) {
                        if (scheduled.retryCount > 0) {
                            TextButton(onClick = onStopRetrying) {
                                Text("Stop retrying")
                            }
                        }
                        TextButton(onClick = onPause) {
                            Text("Pause")
                        }
                    } else if (scheduled.status == ScheduledBookingStatus.PAUSED ||
                        scheduled.status == ScheduledBookingStatus.STOPPED
                    ) {
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


private fun formatTimeUntilNext(nextTime: Long): String {
    val now = System.currentTimeMillis()/1000
    var windowOpen = addDaysToSeconds(nextTime, -7)
    while (windowOpen <= now) {
        windowOpen = addDaysToSeconds(windowOpen, 7)
    }
    val diff = windowOpen - now

    if (diff <= 0) return "soon"

    val days = diff / (24 * 60 * 60)
    val hours = (diff % (24 * 60 * 60 )) / (60 * 60 )
    val minutes = (diff % (60 * 60 )) / (60)

    return when {
        days > 0 -> "in ${days}d ${String.format(Locale.US, "%02d:%02d", hours, minutes)}"
        hours > 0 -> "in ${hours}h ${String.format(Locale.US, "%02d", minutes)}m"
        else -> "in ${minutes}m"
    }
}