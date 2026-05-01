package com.swimgym.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimgym.app.domain.model.ReminderConfig
import com.swimgym.app.domain.model.CalendarIntegrationConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val selectedCalendar: String = "Google Calendar",
    val reminderConfig: ReminderConfig = ReminderConfig(reminderMinutesBefore = 15, reminderEnabled = true),
    val calendarIntegrationConfig: CalendarIntegrationConfig = CalendarIntegrationConfig()
)

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun selectCalendar(calendar: String) {
        _uiState.update { it.copy(selectedCalendar = calendar) }
    }

    fun setReminderConfig(minutes: Int, enabled: Boolean) {
        _uiState.update { it.copy(reminderConfig = ReminderConfig(minutes, enabled)) }
    }

    fun getReminderConfig(): ReminderConfig = _uiState.value.reminderConfig

    fun getCalendarIntegrationConfig(): CalendarIntegrationConfig = _uiState.value.calendarIntegrationConfig
}