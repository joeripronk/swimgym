package com.swimgym.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimgym.app.data.repository.SessionRepository
import com.swimgym.app.domain.model.CalendarInfo
import com.swimgym.app.domain.repository.CalendarRepository
import com.swimgym.app.worker.ScheduledBookingCheckWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

data class ReminderConfig(
    val reminderMinutesBefore: Int = 30,
    val reminderEnabled: Boolean = true,
    val reminderTimes: List<Int> = emptyList(),
    val customTimeEnabled: Boolean = false
)

data class WorkTimeConfig(
    val enabled: Boolean = false,
    val monday: Pair<String, String> = Pair("08:00", "18:00"),
    val mondayEnabled: Boolean = true,
    val tuesday: Pair<String, String> = Pair("08:00", "18:00"),
    val tuesdayEnabled: Boolean = true,
    val wednesday: Pair<String, String> = Pair("08:00", "18:00"),
    val wednesdayEnabled: Boolean = true,
    val thursday: Pair<String, String> = Pair("08:00", "18:00"),
    val thursdayEnabled: Boolean = true,
    val friday: Pair<String, String> = Pair("08:00", "18:00"),
    val fridayEnabled: Boolean = true,
    val saturday: Pair<String, String> = Pair("08:00", "18:00"),
    val saturdayEnabled: Boolean = false,
    val sunday: Pair<String, String> = Pair("08:00", "18:00"),
    val sundayEnabled: Boolean = false
)

data class SettingsUiState(
    val selectedCalendar: String = "",
    val selectedCalendarId: Long = -1,
    val availableCalendars: List<CalendarInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val reminderConfig: ReminderConfig = ReminderConfig(),
    val calendarPermissionDenied: Boolean = false,
    val hideFullyBooked: Boolean = false,
    val workTimeConfig: WorkTimeConfig = WorkTimeConfig(),
    val foregroundServiceEnabled: Boolean = false,
    val requestForegroundPermission: Boolean = false,
    val alarmNotificationEnabled: Boolean = true
)

class SettingsViewModel(
    private val sessionRepository: SessionRepository,
    private val calendarRepository: CalendarRepository,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCalendars()
        loadReminderSettings()
        loadHideFullyBookedSetting()
        loadWorkTimeSettings()
        loadForegroundServiceSetting()
        loadAlarmNotificationSetting()
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

    private fun loadForegroundServiceSetting() {
        viewModelScope.launch {
            val enabled = sessionRepository.getForegroundServiceEnabled()
            _uiState.update { it.copy(foregroundServiceEnabled = enabled) }
        }
    }

    private var _requestForegroundPermission: ((Boolean) -> Unit)? = null

    fun setForegroundServiceEnabled(enabled: Boolean) {
        if (enabled && !foregroundPermissionGranted()) {
            _uiState.update { it.copy(requestForegroundPermission = true) }
            _requestForegroundPermission = { granted ->
                _uiState.update { 
                    it.copy(
                        requestForegroundPermission = false,
                        foregroundServiceEnabled = granted
                    ) 
                }
                if (granted) {
                    viewModelScope.launch {
                        sessionRepository.saveForegroundServiceEnabled(true)
                        reloadWorkManager()
                    }
                }
            }
        } else {
            viewModelScope.launch {
                sessionRepository.saveForegroundServiceEnabled(enabled)
                _uiState.update { it.copy(foregroundServiceEnabled = enabled) }
            }
        }
    }

    private fun foregroundPermissionGranted(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= 29 &&
            android.os.Build.VERSION.SDK_INT < 33
    }

    fun onForegroundPermissionResult(granted: Boolean) {
        _requestForegroundPermission?.let { callback ->
            callback(granted)
        }
        if (granted) {
            reloadWorkManager()
        }
    }

    private fun reloadWorkManager() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWork()
        val bookingCheckRequest = PeriodicWorkRequestBuilder<ScheduledBookingCheckWorker>(
            15, TimeUnit.MINUTES
        ).build()
        workManager.enqueueUniquePeriodicWork(
            ScheduledBookingCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            bookingCheckRequest
        )
    }

    private fun loadAlarmNotificationSetting() {
        viewModelScope.launch {
            val enabled = sessionRepository.getAlarmNotificationEnabled()
            _uiState.update { it.copy(alarmNotificationEnabled = enabled) }
        }
    }

    fun setAlarmNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveAlarmNotificationEnabled(enabled)
            _uiState.update { it.copy(alarmNotificationEnabled = enabled) }
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

    private fun loadHideFullyBookedSetting() {
        viewModelScope.launch {
            val hideFullyBooked = sessionRepository.getHideFullyBooked()
            _uiState.update { it.copy(hideFullyBooked = hideFullyBooked) }
        }
    }

    private fun loadWorkTimeSettings() {
        viewModelScope.launch {
            val enabled = sessionRepository.getWorkTimeFilterEnabled()
            val monday = sessionRepository.getWorkTimeForDay(0)
            val mondayEnabled = sessionRepository.getWorkTimeEnabledForDay(0)
            val tuesday = sessionRepository.getWorkTimeForDay(1)
            val tuesdayEnabled = sessionRepository.getWorkTimeEnabledForDay(1)
            val wednesday = sessionRepository.getWorkTimeForDay(2)
            val wednesdayEnabled = sessionRepository.getWorkTimeEnabledForDay(2)
            val thursday = sessionRepository.getWorkTimeForDay(3)
            val thursdayEnabled = sessionRepository.getWorkTimeEnabledForDay(3)
            val friday = sessionRepository.getWorkTimeForDay(4)
            val fridayEnabled = sessionRepository.getWorkTimeEnabledForDay(4)
            val saturday = sessionRepository.getWorkTimeForDay(5)
            val saturdayEnabled = sessionRepository.getWorkTimeEnabledForDay(5)
            val sunday = sessionRepository.getWorkTimeForDay(6)
            val sundayEnabled = sessionRepository.getWorkTimeEnabledForDay(6)
            _uiState.update {
                it.copy(
                    workTimeConfig = WorkTimeConfig(
                        enabled = enabled,
                        monday = monday,
                        mondayEnabled = mondayEnabled,
                        tuesday = tuesday,
                        tuesdayEnabled = tuesdayEnabled,
                        wednesday = wednesday,
                        wednesdayEnabled = wednesdayEnabled,
                        thursday = thursday,
                        thursdayEnabled = thursdayEnabled,
                        friday = friday,
                        fridayEnabled = fridayEnabled,
                        saturday = saturday,
                        saturdayEnabled = saturdayEnabled,
                        sunday = sunday,
                        sundayEnabled = sundayEnabled
                    )
                )
            }
        }
    }

    fun setHideFullyBooked(hide: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveHideFullyBooked(hide)
            _uiState.update { it.copy(hideFullyBooked = hide) }
        }
    }

    fun setWorkTimeFilterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveWorkTimeFilterEnabled(enabled)
            _uiState.update { 
                it.copy(
                    workTimeConfig = it.workTimeConfig.copy(enabled = enabled)
                )
            }
        }
    }

    fun setWorkTimeForDay(day: Int, startTime: String, endTime: String) {
        viewModelScope.launch {
            sessionRepository.saveWorkTimeForDay(day, startTime, endTime)
            val currentConfig = _uiState.value.workTimeConfig
            val newConfig = when (day) {
                0 -> currentConfig.copy(monday = Pair(startTime, endTime))
                1 -> currentConfig.copy(tuesday = Pair(startTime, endTime))
                2 -> currentConfig.copy(wednesday = Pair(startTime, endTime))
                3 -> currentConfig.copy(thursday = Pair(startTime, endTime))
                4 -> currentConfig.copy(friday = Pair(startTime, endTime))
                5 -> currentConfig.copy(saturday = Pair(startTime, endTime))
                6 -> currentConfig.copy(sunday = Pair(startTime, endTime))
                else -> currentConfig
            }
            _uiState.update { it.copy(workTimeConfig = newConfig) }
        }
    }

    fun setWorkTimeEnabledForDay(day: Int, enabled: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveWorkTimeEnabledForDay(day, enabled)
            val currentConfig = _uiState.value.workTimeConfig
            val newConfig = when (day) {
                0 -> currentConfig.copy(mondayEnabled = enabled)
                1 -> currentConfig.copy(tuesdayEnabled = enabled)
                2 -> currentConfig.copy(wednesdayEnabled = enabled)
                3 -> currentConfig.copy(thursdayEnabled = enabled)
                4 -> currentConfig.copy(fridayEnabled = enabled)
                5 -> currentConfig.copy(saturdayEnabled = enabled)
                6 -> currentConfig.copy(sundayEnabled = enabled)
                else -> currentConfig
            }
            _uiState.update { it.copy(workTimeConfig = newConfig) }
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