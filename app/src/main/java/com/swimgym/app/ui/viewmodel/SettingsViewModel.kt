package com.swimgym.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimgym.app.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ReminderConfig(
    val reminderMinutesBefore: Int = 30,
    val reminderEnabled: Boolean = true
)

data class SettingsUiState(
    val selectedCalendar: String = "Google Calendar",
    val isLoading: Boolean = false,
    val error: String? = null,
    val reminderConfig: ReminderConfig = ReminderConfig()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.selectedCalendar.collect { calendar ->
                if (calendar != null) {
                    _uiState.update { it.copy(selectedCalendar = calendar) }
                }
            }
        }
    }

    fun selectCalendar(calendar: String) {
        viewModelScope.launch {
            sessionRepository.saveSelectedCalendar(calendar)
            _uiState.update { it.copy(selectedCalendar = calendar) }
        }
    }

    fun setReminderConfig(minutes: Int, enabled: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveReminderConfig(minutes, enabled)
            _uiState.update { it.copy(reminderConfig = ReminderConfig(minutes, enabled)) }
        }
    }
}