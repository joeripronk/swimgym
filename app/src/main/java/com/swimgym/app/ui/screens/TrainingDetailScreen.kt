package com.swimgym.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingDetailScreen(
    trainingId: String,
    trainings: List<com.swimgym.app.domain.model.Training>,
    trainerImageUrl: String? = null,
    onBack: () -> Unit,
    onBook: (com.swimgym.app.domain.model.Training) -> Unit,
    onCancel: (com.swimgym.app.domain.model.Training) -> Unit,
    onSchedule: (String, String, String, String, Int?) -> Unit
) {
    val training = trainings.find { it.id == trainingId }

    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var isProcessing by remember { mutableStateOf(false) }

    val displayImageUrl = trainerImageUrl ?: training?.imageUrl ?: ""

    if (training == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            if (displayImageUrl.isNotEmpty()) {
                AsyncImage(
                    model = displayImageUrl,
                    contentDescription = training.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = training.title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                val uriHandler = LocalUriHandler.current
                val instructorText = if (training.instructorLink.isNotEmpty()) {
                    "${training.instructor} ↗"
                } else {
                    "${training.instructor}"
                }

                Text(
                    text = instructorText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (training.instructorLink.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = if (training.instructorLink.isNotEmpty()) {
                        Modifier.clickable { uriHandler.openUri("https://swimgym.virtuagym.com${training.instructorLink}") }
                    } else {
                        Modifier
                    }
                )
                if (training.isJoined) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Registered",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }


            Card(modifier = Modifier.fillMaxWidth()) {
                Row(                verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = dateFormat.format(Date(training.startTime*1000)),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${timeFormat.format(Date(training.startTime*1000))} - ${timeFormat.format(Date(training.endTime*1000))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = training.location,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (training.cost.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Cost: ${training.cost}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (training.totalSpots > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Spots: ${training.spotsAvailable} / ${training.totalSpots}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (training.spotsAvailable > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            if (training.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = training.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (training.cancelPolicy.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = training.cancelPolicy,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Schedule recurring booking section
            var showScheduleDialog by remember { mutableStateOf(false) }
            var maxRepeatCount by remember { mutableStateOf("") }

            Button(
                onClick = { showScheduleDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Schedule Weekly Booking")
            }

            if (showScheduleDialog) {
                AlertDialog(
                    onDismissRequest = { showScheduleDialog = false },
                    title = { Text("Schedule Recurring Booking") },
                    text = {
                        Column {
                            Text("This will automatically book this training every week.")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Optional: Limit number of bookings:")
                            TextField(
                                value = maxRepeatCount,
                                onValueChange = { maxRepeatCount = it },
                                label = { Text("Max bookings (leave empty for unlimited)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val maxCount = maxRepeatCount.toIntOrNull()
                            onSchedule(training.id, training.title, training.classTime, training.classDate, maxCount)
                            showScheduleDialog = false
                        }) {
                            Text("Schedule")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showScheduleDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (training.isJoined) {
                Text(
                    text = "You are registered for this training",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        isProcessing = true
                        onCancel(training)
                        isProcessing = false
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Text("Cancel Training")
                    }
                }
            } else if (training.spotsAvailable > 0) {
                Text(
                    text = "${training.spotsAvailable} spots available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        isProcessing = true
                        onBook(training)
                        isProcessing = false
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Book Training")
                    }
                }
            } else {
                Text(
                    text = "This training is full",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}