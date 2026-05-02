package com.swimgym.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimgym.app.data.repository.SessionRepository
import com.swimgym.app.domain.model.CalendarInfo
import com.swimgym.app.domain.repository.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ReminderConfig(
    val reminderMinutesBefore: Int = 30,
    val reminderEnabled: Boolean = true
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCalendars()
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

    fun setReminderConfig(minutes: Int, enabled: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveReminderConfig(minutes, enabled)
            _uiState.update { it.copy(reminderConfig = ReminderConfig(minutes, enabled)) }
        }
    }
}