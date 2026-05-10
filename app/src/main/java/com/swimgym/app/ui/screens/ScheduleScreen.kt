package com.swimgym.app.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ContentScale.Companion.Fit
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import com.swimgym.app.data.api.WebScraper
import com.swimgym.app.di.SwimGymAppContainer
import com.swimgym.app.domain.model.Training
import com.swimgym.app.domain.repository.SwodLevel
import com.swimgym.app.ui.viewmodel.ScheduleViewModel
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onTrainingClick: (Training) -> Unit,
    onMyBookingsClick: () -> Unit,
    onLogout: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var trainingToAddToCalendar by remember { mutableStateOf<Training?>(null) }
    var showLevelMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()



    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.WRITE_CALENDAR] == true && trainingToAddToCalendar != null) {
            val training = trainingToAddToCalendar!!
            trainingToAddToCalendar = null
            coroutineScope.launch {
                SwimGymAppContainer.getInstance().webScraper.addTrainingToCalendar(training, context)
            }
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
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onLogout) {
                        Text("Log out")
                    }
                    IconButton(onClick = { viewModel.refreshSchedule() }) {
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
    val now = remember { System.currentTimeMillis()/1000 }
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("EEE HH:mm", Locale.getDefault()) }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (training.isJoined) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "${dayFormat.format(Date(training.startTime*1000))} ${training.title}",
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
                        text = "${dateFormat.format(Date(training.startTime*1000))} ${timeFormat.format(Date(training.startTime*1000))}-${timeFormat.format(Date(training.endTime*1000))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

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
                    } else if (training.startTime>now+7*86400) {
                        Text(
                            text = "too early to book",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (!training.isFull) {
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



            }
            if (training.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = training.imageUrl,
                    contentDescription = "Instructor image",

                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .align(Alignment.CenterEnd),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}


