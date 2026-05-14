package com.swimgym.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimgym.app.data.repository.SessionRepository
import com.swimgym.app.domain.model.CalendarInfo
import com.swimgym.app.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ReminderConfig(
    val reminderMinutesBefore: Int = 30,
    val reminderEnabled: Boolean = true,
    val reminderTimes: List<Int> = emptyList(),
    val customTimeEnabled: Boolean = false
)

data class SettingsUiState(
    val selectedCalendar: String = "",
    val selectedCalendarId: Long = -1,
    val availableCalendars: List<CalendarInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val reminderConfig: ReminderConfig = ReminderConfig(),
    val calendarPermissionDenied: Boolean = false
)

class SettingsViewModel(
    private val sessionRepository: SessionRepository,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCalendars()
        loadReminderSettings()
        viewModelScope.launch {
            sessionRepository.selectedCalendar.collect { calendar ->
                if (calendar != null) {
                    _uiState.update { it.copy(selectedCalendar = calendar) }
                }
            }
        }
        viewModelScope.launch {
            sessionRepository.selectedCalendarId.collect { calendarId ->
                if (calendarId != null) {
                    _uiState.update { it.copy(selectedCalendarId = calendarId) }
                }
            }
        }
    }

    private fun loadReminderSettings() {
        viewModelScope.launch {
            val times = sessionRepository.getReminderTimes()
            val enabled = sessionRepository.getReminderEnabled()
            val minutes = sessionRepository.getReminderMinutes()
            _uiState.update {
                it.copy(
                    reminderConfig = ReminderConfig(
                        reminderMinutesBefore = minutes,
                        reminderEnabled = enabled,
                        reminderTimes = if (times.isNotEmpty()) times else listOf(minutes),
                        customTimeEnabled = times.isNotEmpty() && (times.size > 1 || times.first() != 30)
                    )
                )
            }
        }
    }

    private fun loadCalendars() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, calendarPermissionDenied = false) }
            try {
                val calendars = calendarRepository.getAvailableCalendars()
                _uiState.update { it.copy(availableCalendars = calendars, isLoading = false) }
            } catch (e: SecurityException) {
                _uiState.update { it.copy(error = "Calendar permission denied", isLoading = false, calendarPermissionDenied = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun refreshCalendars() {
        loadCalendars()
    }

    fun selectCalendar(calendarId: Long, calendarName: String) {
        viewModelScope.launch {
            sessionRepository.saveSelectedCalendar(calendarName)
            sessionRepository.saveSelectedCalendarId(calendarId)
            _uiState.update { it.copy(selectedCalendar = calendarName, selectedCalendarId = calendarId) }
        }
    }

    fun selectCalendarAsNone() {
        viewModelScope.launch {
            sessionRepository.saveSelectedCalendar("")
            sessionRepository.saveSelectedCalendarId(0)
            _uiState.update { it.copy(selectedCalendar = "", selectedCalendarId = 0L) }
        }
    }

    fun setReminderConfig(minutes: Int, enabled: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveReminderConfig(minutes, enabled)
            _uiState.update { it.copy(reminderConfig = ReminderConfig(minutes, enabled, emptyList(), false)) }
        }
    }

    fun setReminderTimes(times: List<Int>, customEnabled: Boolean = true) {
        viewModelScope.launch {
            sessionRepository.saveReminderTimes(times)
            _uiState.update { 
                it.copy(
                    reminderConfig = ReminderConfig(
                        reminderMinutesBefore = times.firstOrNull() ?: 30,
                        reminderEnabled = times.isNotEmpty(),
                        reminderTimes = times,
                        customTimeEnabled = customEnabled
                    )
                )
            }
        }
    }

    fun addReminderTime(minutes: Int) {
        viewModelScope.launch {
            val currentTimes = _uiState.value.reminderConfig.reminderTimes.toMutableList()
            if (!currentTimes.contains(minutes)) {
                currentTimes.add(minutes)
                currentTimes.sort()
                setReminderTimes(currentTimes)
            }
        }
    }

    fun removeReminderTime(minutes: Int) {
        viewModelScope.launch {
            val currentTimes = _uiState.value.reminderConfig.reminderTimes.toMutableList()
            currentTimes.remove(minutes)
            setReminderTimes(currentTimes)
        }
    }
}