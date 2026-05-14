package com.swimgym.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swimgym.app.ui.viewmodel.SettingsViewModel
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import com.swimgym.app.util.PermissionHelper

@ExperimentalComposeUiApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.refreshCalendars()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled
    }

    val context = LocalContext.current
    val hasNotificationPermission by remember {
        derivedStateOf {
            PermissionHelper.hasNotificationPermission(context)
        }
    }

    LaunchedEffect(Unit) {
        calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        modifier = Modifier.clickable {
                            onBack()
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Default Calendar",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Select your default calendar for bookings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.calendarPermissionDenied) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Calendar permission is required to list available calendars.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                                Text("Grant Permission")
                            }
                        }
                    } else if (uiState.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (uiState.error != null) {
                        Text(
                            text = "Error: ${uiState.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (uiState.availableCalendars.isEmpty()) {
                        Text(
                            text = "No calendars found. Please add a calendar account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                   } else {
                          var dropdownExpanded by remember { mutableStateOf(false) }
                          
                          Box(
                              modifier = Modifier.fillMaxWidth()
                          ) {
                              TextButton(onClick = { dropdownExpanded = true }) {
                                  Text(
                                      uiState.selectedCalendar.ifEmpty { "Select Calendar" }

                                  )
                              }

                              DropdownMenu(
                                   expanded = dropdownExpanded,
                                   onDismissRequest = { dropdownExpanded = false }
                               ) {
                                   DropdownMenuItem(
                                       onClick = {
                                           viewModel.selectCalendarAsNone()
                                           dropdownExpanded = false
                                       },
                                       text = {
                                           Row(
                                               horizontalArrangement = Arrangement.SpaceBetween,
                                               verticalAlignment = Alignment.CenterVertically
                                           ) {
                                               Text(
                                                   text = "None (Disable calendar)",
                                                   style = MaterialTheme.typography.bodyMedium
                                               )
                                               Spacer(modifier = Modifier.weight(1f))
                                               if (uiState.selectedCalendarId == 0L) {
                                                   Icon(
                                                       imageVector = Icons.Default.CheckCircle,
                                                       contentDescription = "Selected",
                                                       tint = MaterialTheme.colorScheme.primary
                                                   )
                                               }
                                           }
                                       }
                                   )
                                   uiState.availableCalendars.forEach { calendar ->
                                       DropdownMenuItem(
                                           onClick = {
                                               viewModel.selectCalendar(calendar.id, calendar.displayName)
                                               dropdownExpanded = false
                                           },
                                           text = {
                                               Row(
                                                   horizontalArrangement = Arrangement.SpaceBetween,
                                                   verticalAlignment = Alignment.CenterVertically
                                               ) {
                                                   Text(
                                                       text = calendar.displayName,
                                                       style = MaterialTheme.typography.bodyMedium
                                                   )
                                                   Spacer(modifier = Modifier.weight(1f))
                                                   if (uiState.selectedCalendarId == calendar.id) {
                                                       Icon(
                                                           imageVector = Icons.Default.CheckCircle,
                                                           contentDescription = "Selected",
                                                           tint = MaterialTheme.colorScheme.primary
                                                       )
                                                   }
                                               }
                                           }
                                       )
                                   }
                               }
                          }
                      }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Notification Permissions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Enable booking confirmations and reminders",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasNotificationPermission) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Notifications enabled",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Enabled",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications disabled",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Disabled",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        if (!hasNotificationPermission) {
                            Button(
                                onClick = { 
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }
                            ) {
                                Text("Enable")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Allow the app to send you booking confirmations and training reminders.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Reminder Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Configure booking reminders",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Select preset reminder times (can select multiple):",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { viewModel.addReminderTime(5) }) {
                            Text("5 min")
                        }
                        TextButton(onClick = { viewModel.addReminderTime(15) }) {
                            Text("15 min")
                        }
                        TextButton(onClick = { viewModel.addReminderTime(30) }) {
                            Text("30 min")
                        }
                        TextButton(onClick = { viewModel.addReminderTime(60) }) {
                            Text("1 hour")
                        }
                    }

                    Text(
                        text = "Custom time (minutes before):",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var customTime by remember { mutableStateOf("10") }
                        OutlinedTextField(
                            value = customTime,
                            onValueChange = { customTime = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("e.g. 10") },
                            enabled = true
                        )
                        Button(
                            onClick = {
                                customTime.toIntOrNull()?.let { time ->
                                    if (time > 0) viewModel.addReminderTime(time)
                                }
                            }
                        ) {
                            Text("Add")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Current reminders:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (uiState.reminderConfig.reminderTimes.isEmpty()) {
                        Text(
                            text = "No reminders configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.reminderConfig.reminderTimes.forEach { time ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$time minutes before",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                IconButton(
                                    onClick = { viewModel.removeReminderTime(time) }
                                ) {
                                    Text("✕", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { viewModel.setReminderConfig(0, false) }) {
                            Text("Disable all reminders")
                        }
                        TextButton(onClick = { 
                            viewModel.setReminderTimes(emptyList()) 
                            viewModel.setReminderConfig(30, true)
                        }) {
                            Text("Reset to default (30 min)")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Schedule Display",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Hide fully booked classes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hide classes that are fully booked from the schedule",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Switch(
                            checked = uiState.hideFullyBooked,
                            onCheckedChange = { viewModel.setHideFullyBooked(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Work Time Filter",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Filter trainings by your work hours",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable work time filter",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Only show trainings during your work hours",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = uiState.workTimeConfig.enabled,
                            onCheckedChange = { viewModel.setWorkTimeFilterEnabled(it) }
                        )
                    }

                    if (uiState.workTimeConfig.enabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Set work hours for each day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val days = listOf(
                            "Monday" to 0,
                            "Tuesday" to 1,
                            "Wednesday" to 2,
                            "Thursday" to 3,
                            "Friday" to 4,
                            "Saturday" to 5,
                            "Sunday" to 6
                        )

                        LazyColumn(
                            modifier = Modifier.height(300.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(days) { (dayName, dayIndex) ->
                                val times = when (dayIndex) {
                                    0 -> uiState.workTimeConfig.monday
                                    1 -> uiState.workTimeConfig.tuesday
                                    2 -> uiState.workTimeConfig.wednesday
                                    3 -> uiState.workTimeConfig.thursday
                                    4 -> uiState.workTimeConfig.friday
                                    5 -> uiState.workTimeConfig.saturday
                                    else -> uiState.workTimeConfig.sunday
                                }
                                var timePickerVisible by remember { mutableStateOf(false) }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dayName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = times.first,
                                            onValueChange = { 
                                                if (it.matches(Regex("\\d{2}:\\d{2}"))) {
                                                    viewModel.setWorkTimeForDay(dayIndex, it, times.second)
                                                }
                                            },
                                            modifier = Modifier.width(80.dp),
                                            singleLine = true,
                                            placeholder = { Text("08:00") },
                                            enabled = true
                                        )
                                        Text("to")
                                        OutlinedTextField(
                                            value = times.second,
                                            onValueChange = { 
                                                if (it.matches(Regex("\\d{2}:\\d{2}"))) {
                                                    viewModel.setWorkTimeForDay(dayIndex, times.first, it)
                                                }
                                            },
                                            modifier = Modifier.width(80.dp),
                                            singleLine = true,
                                            placeholder = { Text("18:00") },
                                            enabled = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}