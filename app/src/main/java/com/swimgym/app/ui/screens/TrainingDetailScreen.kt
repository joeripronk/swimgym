package com.swimgym.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.swimgym.app.data.api.WebScraper
import com.swimgym.app.di.SwimGymAppContainer
import com.swimgym.app.domain.model.Training
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
   fun TrainingDetailScreen(
        training: Training?,
        onBack: () -> Unit,
        onBook: (com.swimgym.app.domain.model.Training) -> Unit,
        onCancel: (com.swimgym.app.domain.model.Training) -> Unit,
        onSchedule: (String, String, String, Int?) -> Unit,
        onAddToCalendar: (Training) -> Unit,
        onNavigateToMyBookings: () -> Unit,
        onRefresh: (String) -> Unit,
        onBookingSuccess: (String) -> Unit,
        onCancellationSuccess: (String) -> Unit,
        isBookingInProgress: Boolean,
        isCancellingInProgress: Boolean,
        bookingError: String? = null,
        cancelError: String? = null,
        onClearBookingError: () -> Unit = {},
        onClearCancelError: () -> Unit = {}
    ) {
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var trainingToAddToCalendar by remember { mutableStateOf<Training?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.WRITE_CALENDAR] == true && trainingToAddToCalendar != null) {
            val training = trainingToAddToCalendar!!
            trainingToAddToCalendar = null
            CoroutineScope(Dispatchers.IO).launch {
                SwimGymAppContainer.getInstance().webScraper.addTrainingToCalendar(training)
            }
        }
    }

    val displayImageUrl = training?.imageUrl
    if (training == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var maxRepeatCount by remember { mutableStateOf("") }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                if (displayImageUrl?.isNotEmpty()==true) {
                    AsyncImage(
                        model = displayImageUrl,
                        contentDescription = training.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            item {
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
            }

            item {
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
            }

            item {
                Text(
                    text = training.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (training.cost.isNotEmpty()) {
                item {
                    Text(
                        text = "Cost: ${training.cost}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (training.totalSpots > 0) {
                item {
                    Text(
                        text = "Spots: ${training.spotsAvailable} / ${training.totalSpots}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (training.spotsAvailable > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            if (training.description.isNotEmpty()) {
                item {
                    Text(
                        text = training.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (training.cancelPolicy.isNotEmpty()) {
                item {
                    Text(
                        text = training.cancelPolicy,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Button(
                    onClick = { showScheduleDialog = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Schedule Weekly Booking")
                }
            }

            item {
                if (training.isJoined) {
                    Text(
                        text = "You are registered for this training",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                  Button(
                         onClick = {
                             onAddToCalendar(training)
                             trainingToAddToCalendar = training
                             permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_CALENDAR))
                         },
                         modifier = Modifier.fillMaxWidth().height(50.dp),
                         colors = ButtonDefaults.buttonColors(
                             containerColor = MaterialTheme.colorScheme.secondary
                         )
                     ) {
                         Text("+ Add to Calendar")
                     }

              Button(
                          onClick = {
                              onCancel(training)
                              onCancellationSuccess(training.id)
                          },
                          enabled = !isCancellingInProgress,
                          modifier = Modifier
                              .fillMaxWidth()
                              .height(50.dp),
                          colors = ButtonDefaults.buttonColors(
                              containerColor = if (isCancellingInProgress) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error,
                              disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                          )
                      ) {
                          if (isCancellingInProgress) {
                              Row(
                                  horizontalArrangement = Arrangement.Center,
                                  verticalAlignment = Alignment.CenterVertically
                              ) {
                                  CircularProgressIndicator(
                                      modifier = Modifier.size(24.dp),
                                      color = MaterialTheme.colorScheme.onError
                                  )
                                  Spacer(modifier = Modifier.width(8.dp))
                                  Text("Cancelling booking...")
                              }
                          } else {
                              Text("Cancel Training")
                          }
                      }
                } else if (training.spotsAvailable > 0  || !training.isFull) {
                    Text(
                        text = "${training.spotsAvailable} spots available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                Button(
                         onClick = {
                             onBook(training)
                             onBookingSuccess(training.id)
                         },
                         enabled = !isBookingInProgress,
                         modifier = Modifier
                             .fillMaxWidth()
                             .height(50.dp),
                         colors = ButtonDefaults.buttonColors(
                             containerColor = if (isBookingInProgress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                             disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                         )
                     ) {
                         if (isBookingInProgress) {
                             Row(
                                 horizontalArrangement = Arrangement.Center,
                                 verticalAlignment = Alignment.CenterVertically
                             ) {
                                 CircularProgressIndicator(
                                     modifier = Modifier.size(24.dp),
                                     color = MaterialTheme.colorScheme.onPrimary
                                 )
                                 Spacer(modifier = Modifier.width(8.dp))
                                 Text("Booking in progress...")
                             }
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
                      onSchedule(training.id, training.title, training.instructor, maxCount)
                       showScheduleDialog = false
                       onNavigateToMyBookings()
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

    if (bookingError != null) {
        AlertDialog(
            onDismissRequest = { onClearBookingError() },
            title = { Text("Booking Failed") },
            text = { Text(bookingError) },
            confirmButton = {
                Button(onClick = { onClearBookingError() }) {
                    Text("OK")
                }
            }
        )
    }

    if (cancelError != null) {
        AlertDialog(
            onDismissRequest = { onClearCancelError() },
            title = { Text("Cancellation Failed") },
            text = { Text(cancelError) },
            confirmButton = {
                Button(onClick = { onClearCancelError() }) {
                    Text("OK")
                }
            }
        )
    }
}