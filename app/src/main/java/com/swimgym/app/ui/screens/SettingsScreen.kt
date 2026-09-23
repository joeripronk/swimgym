package com.swimgym.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.swimgym.app.ui.viewmodel.SettingsViewModel
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

    var showReleaseNotesDialog by remember { mutableStateOf(false) }

    val installApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // APK install result handled
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
                text = "Background Sync",
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
                        text = "Notify on schedule sync",
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
                                text = "Show a notification",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Notifies every time the schedule syncs in the background (every 12 hours)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = uiState.alarmNotificationEnabled,
                            onCheckedChange = { viewModel.setAlarmNotificationEnabled(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Exact Alarms",
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
                        text = "Ensure timely booking reminders",
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
                                text = "Use exact alarms for booking reminders",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Guarantees reminders fire at the exact scheduled time, even during Doze mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = uiState.exactAlarmEnabled,
                            onCheckedChange = { viewModel.setExactAlarmEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!PermissionHelper.hasExactAlarmPermission(context)) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Exact alarm permission not granted. Booking reminders may be delayed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Text("Grant Permission")
                                }
                            }
                        }
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

                        var showTimePicker by remember { mutableStateOf(false) }
                        var selectedDayIndex by remember { mutableStateOf(-1) }
                        var selectedTimeType by remember { mutableStateOf("start") }
                        
                        LazyColumn(
                            modifier = Modifier.height(350.dp),
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
                                val isEnabled = when (dayIndex) {
                                    0 -> uiState.workTimeConfig.mondayEnabled
                                    1 -> uiState.workTimeConfig.tuesdayEnabled
                                    2 -> uiState.workTimeConfig.wednesdayEnabled
                                    3 -> uiState.workTimeConfig.thursdayEnabled
                                    4 -> uiState.workTimeConfig.fridayEnabled
                                    5 -> uiState.workTimeConfig.saturdayEnabled
                                    else -> uiState.workTimeConfig.sundayEnabled
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { 
                                            viewModel.setWorkTimeEnabledForDay(dayIndex, it)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                            uncheckedThumbColor = MaterialTheme.colorScheme.error,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                        )
                                    )
                                    
                                    Text(
                                        text = dayName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    
                                    Spacer(modifier = Modifier.weight(1f))
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = times.first,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp)
                                                .clickable {
                                                    selectedDayIndex = dayIndex
                                                    selectedTimeType = "start"
                                                    showTimePicker = true
                                                }
                                        )
                                        Text("to")
                                        Text(
                                            text = times.second,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp)
                                                .clickable {
                                                    selectedDayIndex = dayIndex
                                                    selectedTimeType = "end"
                                                    showTimePicker = true
                                                }
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (showTimePicker && selectedDayIndex >= 0) {
                            val currentTimes = when (selectedDayIndex) {
                                0 -> uiState.workTimeConfig.monday
                                1 -> uiState.workTimeConfig.tuesday
                                2 -> uiState.workTimeConfig.wednesday
                                3 -> uiState.workTimeConfig.thursday
                                4 -> uiState.workTimeConfig.friday
                                5 -> uiState.workTimeConfig.saturday
                                else -> uiState.workTimeConfig.sunday
                            }
                            val timeToUse = if (selectedTimeType == "end") currentTimes.second else currentTimes.first
                            val initialHour = timeToUse.split(":").getOrNull(0)?.toIntOrNull() ?: 9
                            val initialMinute = timeToUse.split(":").getOrNull(1)?.toIntOrNull() ?: 0
                            
                            TimePickerDialog(
                                onDismissRequest = { showTimePicker = false },
                                onTimeSelected = { hours, minutes ->
                                    val timeStr = String.format("%02d:%02d", hours, minutes)
                                    if (selectedTimeType == "start") {
                                        viewModel.setWorkTimeForDay(selectedDayIndex, timeStr, currentTimes.second)
                                    } else {
                                        viewModel.setWorkTimeForDay(selectedDayIndex, currentTimes.first, timeStr)
                                    }
                                    showTimePicker = false
                                },
                                initialHour = initialHour,
                                initialMinute = initialMinute
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "App Updates",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Check for Updates",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Current version: ${context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (uiState.isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Button(
                                onClick = { viewModel.checkForUpdates(context) },
                                enabled = !uiState.isCheckingUpdate
                            ) {
                                Text("Check")
                            }
                        }
                    }

                    if (uiState.updateAvailable) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Update available",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Update available!",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Version ${uiState.latestVersion} is available",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { showReleaseNotesDialog = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Notes")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.uiState.value.releaseUrl.let { url ->
                                                if (url.isNotEmpty()) {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    context.startActivity(intent)
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = !uiState.isDownloading && !uiState.isInstalling
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("View")
                                    }
                                    Button(
                                        onClick = { viewModel.installUpdate(context) },
                                        modifier = Modifier.weight(1f),
                                        enabled = !uiState.isDownloading && !uiState.isInstalling
                                    ) {
                                        if (uiState.isDownloading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else if (uiState.isInstalling) {
                                            Text("Installing...")
                                        } else {
                                            Text("Install")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.updateCheckError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Could not check for updates: ${uiState.updateCheckError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (uiState.installError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Installation failed: ${uiState.installError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showReleaseNotesDialog) {
        ReleaseNotesDialog(
            onDismissRequest = { showReleaseNotesDialog = false },
            releaseNotes = uiState.releaseNotes,
            latestVersion = uiState.latestVersion,
            onInstall = {
                showReleaseNotesDialog = false
                uiState.releaseUrl.let { url ->
                    if (url.isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                }
            }
        )
    }
}

@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onTimeSelected: (Int, Int) -> Unit,
    initialHour: Int = 9,
    initialMinute: Int = 0
) {
    var hour by remember { mutableStateOf(initialHour) }
    var minute by remember { mutableStateOf(initialMinute) }
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select Time") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Hours",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (h in 0..23) {
                        val isSelected = h == hour
                        Button(
                            onClick = { hour = h },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("$h")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Minutes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (m in (0..59 step 5)) {
                        val isSelected = m == minute
                        Button(
                            onClick = { minute = m },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("$m")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onTimeSelected(hour, minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ReleaseNotesDialog(
    onDismissRequest: () -> Unit,
    releaseNotes: String,
    latestVersion: String,
    onInstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text("Update $latestVersion")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (releaseNotes.isNotBlank()) {
                    Text(
                        text = releaseNotes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "No release notes available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onInstall) {
                Text("Install")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}