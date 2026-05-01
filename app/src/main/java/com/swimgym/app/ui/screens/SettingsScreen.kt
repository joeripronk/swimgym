package com.swimgym.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swimgym.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Card(
                            onClick = { viewModel.selectCalendar("Google Calendar") },
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.selectCalendar("Google Calendar") }
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Google Calendar",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (uiState.selectedCalendar == "Google Calendar") {
                                    Text(
                                        text = "Selected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Card(
                            onClick = { viewModel.selectCalendar("Apple Calendar") },
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.selectCalendar("Apple Calendar") }
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Apple Calendar",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (uiState.selectedCalendar == "Apple Calendar") {
                                    Text(
                                        text = "Selected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
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
                        text = "Enable reminders",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { viewModel.setReminderConfig(15, true) }) {
                            Text("15 minutes before")
                        }
                        TextButton(onClick = { viewModel.setReminderConfig(30, true) }) {
                            Text("30 minutes before")
                        }
                        TextButton(onClick = { viewModel.setReminderConfig(60, true) }) {
                            Text("1 hour before")
                        }
                    }

                    TextButton(onClick = { viewModel.setReminderConfig(0, false) }) {
                        Text("Disable reminders")
                    }

                    Text(
                        text = "Configured: ${uiState.reminderConfig.reminderMinutesBefore} minutes before",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Reminders enabled: ${uiState.reminderConfig.reminderEnabled}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}