package com.swimgym.app.ui.screens

import android.Manifest
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swimgym.app.domain.model.Training
import com.swimgym.app.domain.repository.SwodLevel
import com.swimgym.app.ui.viewmodel.ScheduleViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onTrainingClick: (Training) -> Unit,
    onMyBookingsClick: () -> Unit,
    onLogout: () -> Unit,
    viewModel: ScheduleViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var trainingToAddToCalendar by remember { mutableStateOf<Training?>(null) }
    var showLevelMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Infinite scroll - load more when reaching bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.maxByOrNull { it.index }?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount

            totalItems > 0 && lastVisibleItem >= totalItems - 3 && !uiState.isLoadingMore && uiState.weeksLoaded <= 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadNextWeek()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.WRITE_CALENDAR] == true && trainingToAddToCalendar != null) {
            addTrainingToCalendar(context, trainingToAddToCalendar!!)
            trainingToAddToCalendar = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training Schedule") },
                actions = {
                    Box {
                        TextButton(onClick = { showLevelMenu = true }) {
                            Text(uiState.selectedLevel.name)
                        }
                        DropdownMenu(
                            expanded = showLevelMenu,
                            onDismissRequest = { showLevelMenu = false }
                        ) {
                            SwodLevel.values().forEach { level ->
                                DropdownMenuItem(
                                    text = { Text(level.name) },
                                    onClick = {
                                        viewModel.setLevel(level)
                                        showLevelMenu = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = onMyBookingsClick) {
                        Icon(Icons.Default.List, contentDescription = "My Bookings")
                    }
                    IconButton(onClick = onLogout) {
                        Text("Log out")
                    }
                    IconButton(onClick = { viewModel.loadSchedule() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = uiState.error ?: "Error",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadSchedule() }) {
                        Text("Retry")
                    }
                }
            }
            uiState.trainings.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No trainings scheduled")
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.trainings) { training ->
                        TrainingCard(
                            training = training,
                            onClick = { onTrainingClick(training) },
                            onAddToCalendar = {
                                trainingToAddToCalendar = training
                                permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_CALENDAR))
                            }
                        )
                    }

                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    if (uiState.weeksLoaded >= 4 && !uiState.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No more trainings available",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainingCard(
    training: Training,
    onClick: () -> Unit,
    onAddToCalendar: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = training.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = training.instructor,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${dateFormat.format(Date(training.startTime))} ${timeFormat.format(Date(training.startTime))}-${timeFormat.format(Date(training.endTime))}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (training.isJoined) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Registered",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Registered",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (training.spotsAvailable > 0) {
                        Text(
                            text = "spot available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Full",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onAddToCalendar) {
                    Text("+ Calendar")
                }
            }
        }
    }
}

private fun addTrainingToCalendar(context: android.content.Context, training: Training) {
    try {
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, training.startTime)
            put(CalendarContract.Events.DTEND, training.endTime)
            put(CalendarContract.Events.TITLE, training.title)
            put(CalendarContract.Events.DESCRIPTION, "Instructor: ${training.instructor}")
            put(CalendarContract.Events.EVENT_LOCATION, training.location)
            put(CalendarContract.Events.CALENDAR_ID, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}