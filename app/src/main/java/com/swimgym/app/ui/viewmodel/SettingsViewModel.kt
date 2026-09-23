package com.swimgym.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimgym.app.BuildConfig
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
    val alarmNotificationEnabled: Boolean = true,
    val exactAlarmEnabled: Boolean = true,
    val updateAvailable: Boolean = false,
    val latestVersion: String = "",
    val releaseNotes: String = "",
    val releaseUrl: String = "",
    val apkDownloadUrl: String? = null,
    val isCheckingUpdate: Boolean = false,
    val updateCheckError: String? = null,
    val isDownloading: Boolean = false,
    val isInstalling: Boolean = false,
    val installError: String? = null
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
        loadHideFullyBookedSetting()
        loadWorkTimeSettings()
        loadAlarmNotificationSetting()
        loadExactAlarmSetting()
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

    private fun loadExactAlarmSetting() {
        viewModelScope.launch {
            val enabled = sessionRepository.getExactAlarmEnabled()
            _uiState.update { it.copy(exactAlarmEnabled = enabled) }
        }
    }

    fun setExactAlarmEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sessionRepository.saveExactAlarmEnabled(enabled)
            _uiState.update { it.copy(exactAlarmEnabled = enabled) }
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

    fun checkForUpdates(context: android.content.Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true, updateCheckError = null) }
            try {
                val result = com.swimgym.app.util.UpdateChecker.checkForUpdates(context)
                result.onSuccess { release ->
                    val currentVersion = BuildConfig.VERSION_NAME
                    val isNewer = com.swimgym.app.util.UpdateChecker.isNewerVersion(currentVersion, release.tagName)
                    _uiState.update {
                        it.copy(
                            updateAvailable = isNewer,
                            latestVersion = release.tagName,
                            releaseNotes = release.body,
                            releaseUrl = release.htmlUrl,
                            apkDownloadUrl = release.apkDownloadUrl,
                            isCheckingUpdate = false
                        )
                    }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            updateCheckError = e.message ?: "Failed to check for updates"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        updateCheckError = e.message ?: "Failed to check for updates"
                    )
                }
            }
        }
    }

    fun installUpdate(context: android.content.Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, installError = null) }
            val downloadUrl = _uiState.value.apkDownloadUrl
            if (downloadUrl.isNullOrEmpty()) {
                _uiState.update { it.copy(isDownloading = false, installError = "No APK download URL available") }
                return@launch
            }

            try {
                val result = com.swimgym.app.util.UpdateChecker.downloadApk(context, downloadUrl)
                result.onSuccess { apkFile ->
                    _uiState.update { it.copy(isDownloading = false, isInstalling = true) }
                    val installIntent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                        .setDataAndType(
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                apkFile
                            ),
                            "application/vnd.android.package-archive"
                        )
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(installIntent)
                    _uiState.update { it.copy(isInstalling = false) }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            installError = "Download failed: ${e.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        installError = e.message ?: "Installation failed"
                    )
                }
            }
        }
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