package com.swimgym.app.ui.viewmodel

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.model.Mappers.toDomain
import com.swimgym.app.data.model.Mappers.toEntity
import com.swimgym.app.data.repository.ScheduledBooking
import com.swimgym.app.data.repository.ScheduledBookingRepository
import com.swimgym.app.data.repository.TrainerImageCache
import com.swimgym.app.di.SwimGymAppContainer
import com.swimgym.app.domain.model.Booking
import com.swimgym.app.domain.model.Training
import com.swimgym.app.domain.repository.SwodLevel
import com.swimgym.app.domain.repository.SyncStatus
import com.swimgym.app.domain.usecase.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)



data class ScheduleUiState(
    val trainings: List<Training> = emptyList(),
    val bookings: List<Booking> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val selectedLevel: SwodLevel = SwodLevel.ALL,
    val hideFullyBooked: Boolean = true,
    val currentStartDate: String? = null,
    val weeksLoaded: Int = 0,
    val syncStatus: SyncStatus = SyncStatus(),
    val isBookingInProgress: Boolean = false,
    val isCancellingInProgress: Boolean = false,
    val bookingError: String? = null,
    val cancelError: String? = null,
    val workTimeFilterEnabled: Boolean = false,
    val workTimeMonday: Pair<String, String> = Pair("08:00", "18:00"),
    val workTimeMondayEnabled: Boolean = true,
    val workTimeTuesday: Pair<String, String> = Pair("08:00", "18:00"),
    val workTimeTuesdayEnabled: Boolean = true,
    val workTimeWednesday: Pair<String, String> = Pair("08:00", "18:00"),
    val workTimeWednesdayEnabled: Boolean = true,
    val workTimeThursday: Pair<String, String> = Pair("08:00", "18:00"),
    val workTimeThursdayEnabled: Boolean = true,
    val workTimeFriday: Pair<String, String> = Pair("08:00", "18:00"),
    val workTimeFridayEnabled: Boolean = true,
    val workTimeSaturday: Pair<String, String> = Pair("08:00", "18:00"),
    val workTimeSaturdayEnabled: Boolean = false,
    val workTimeSunday: Pair<String, String> = Pair("08:00", "18:00"),
    val workTimeSundayEnabled: Boolean = false,
    val calendarError: String? = null
)

class ScheduleViewModel(
    private val getScheduleUseCase: GetScheduleUseCase,
    private val getTrainingDetailsUseCase: GetTrainingDetailsUseCase,
    private val getMyBookingsUseCase: GetMyBookingsUseCase,
    private val bookTrainingUseCase: BookTrainingUseCase,
    private val cancelBookingUseCase: CancelBookingUseCase,
    private val refreshScheduleUseCase: RefreshScheduleUseCase,
    private val getSyncStatusUseCase: GetSyncStatusUseCase,
    private val sessionRepository: com.swimgym.app.data.repository.SessionRepository,
    private val scheduledBookingRepo: ScheduledBookingRepository,
    private val alarmScheduler: com.swimgym.app.util.AlarmScheduler,
    private val trainerImageCache: TrainerImageCache,
    private val dao: com.swimgym.app.data.local.SwimGymDao,
    private val applicationContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _selectedTraining = MutableStateFlow<Training?>(null)
    val selectedTraining: StateFlow<Training?> = _selectedTraining.asStateFlow()

    private val _justBookedTrainingId = MutableStateFlow<String?>(null)
    val justBookedTrainingId: StateFlow<String?> = _justBookedTrainingId.asStateFlow()

    fun clearJustBookedTrainingId() {
        _justBookedTrainingId.value = null
    }

    val imageLoader get() = trainerImageCache.imageLoader

    init {
        // Load trainings from database immediately on startup
        loadSchedule(SwodLevel.ALL, _uiState.value.hideFullyBooked)
        
        viewModelScope.launch {
            sessionRepository.selectedLevel.collect { savedLevel ->
                val level = savedLevel?.let { levelStr ->
                    try { com.swimgym.app.domain.repository.SwodLevel.valueOf(levelStr) } catch (e: Exception) { com.swimgym.app.domain.repository.SwodLevel.ALL }
                } ?: com.swimgym.app.domain.repository.SwodLevel.ALL
                _uiState.update { it.copy(selectedLevel = level) }
            }
        }
        observeBookings()
        observeSyncStatus()
        observeHideFullyBooked()
        observeWorkTimeFilter()
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            getSyncStatusUseCase().collect { syncStatus: SyncStatus ->
                _uiState.update { it.copy(syncStatus = syncStatus) }
            }
        }
    }
    
    private fun observeHideFullyBooked() {
        viewModelScope.launch {
            sessionRepository.hideFullyBooked.distinctUntilChanged().collect { hideFullyBooked ->
                if (hideFullyBooked != _uiState.value.hideFullyBooked) {
                    _uiState.update { it.copy(hideFullyBooked = hideFullyBooked) }
                    loadSchedule(_uiState.value.selectedLevel, hideFullyBooked)
                }
            }
        }
    }
    
    private fun observeWorkTimeFilter() {
        viewModelScope.launch {
            combine(
                sessionRepository.workTimeFilterEnabled,
                sessionRepository.workTimeEnabledDays,
                sessionRepository.workTimeRanges
            ) { enabled, dayEnableds, ranges ->
                Triple(enabled, dayEnableds, ranges)
            }.distinctUntilChanged().collect { (enabled, dayEnableds, ranges) ->
                if (enabled != _uiState.value.workTimeFilterEnabled || 
                    dayEnableds != listOf(
                        _uiState.value.workTimeMondayEnabled,
                        _uiState.value.workTimeTuesdayEnabled,
                        _uiState.value.workTimeWednesdayEnabled,
                        _uiState.value.workTimeThursdayEnabled,
                        _uiState.value.workTimeFridayEnabled,
                        _uiState.value.workTimeSaturdayEnabled,
                        _uiState.value.workTimeSundayEnabled
                    ) || ranges != listOf(
                        _uiState.value.workTimeMonday,
                        _uiState.value.workTimeTuesday,
                        _uiState.value.workTimeWednesday,
                        _uiState.value.workTimeThursday,
                        _uiState.value.workTimeFriday,
                        _uiState.value.workTimeSaturday,
                        _uiState.value.workTimeSunday
                    )) {
                    _uiState.update { it.copy(
                        workTimeFilterEnabled = enabled,
                        workTimeMondayEnabled = dayEnableds[0],
                        workTimeTuesdayEnabled = dayEnableds[1],
                        workTimeWednesdayEnabled = dayEnableds[2],
                        workTimeThursdayEnabled = dayEnableds[3],
                        workTimeFridayEnabled = dayEnableds[4],
                        workTimeSaturdayEnabled = dayEnableds[5],
                        workTimeSundayEnabled = dayEnableds[6],
                        workTimeMonday = ranges[0],
                        workTimeTuesday = ranges[1],
                        workTimeWednesday = ranges[2],
                        workTimeThursday = ranges[3],
                        workTimeFriday = ranges[4],
                        workTimeSaturday = ranges[5],
                        workTimeSunday = ranges[6]
                    ) }
                    loadSchedule(_uiState.value.selectedLevel, _uiState.value.hideFullyBooked)
                }
            }
        }
    }

    private fun observeBookings() {
        viewModelScope.launch {
            getMyBookingsUseCase().collect { bookings ->
                _uiState.update { it.copy(bookings = bookings) }
            }
        }
    }

    fun loadSchedule(
        level: SwodLevel = _uiState.value.selectedLevel,
        hideFullyBooked: Boolean = _uiState.value.hideFullyBooked
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, currentStartDate = null, weeksLoaded = 1) }
            getScheduleUseCase(level, hideFullyBooked, null)
                .onSuccess { trainings ->
                    var filteredTrainings = trainings.filter { it.startTime > System.currentTimeMillis()/1000 }
                    
                    // Apply work time filter if enabled
                    if (_uiState.value.workTimeFilterEnabled) {
                        filteredTrainings = filterByWorkTime(filteredTrainings)
                    }
                    
                    _uiState.update { it.copy(isLoading = false, trainings = filteredTrainings, selectedLevel = level, hideFullyBooked = hideFullyBooked, currentStartDate = null, weeksLoaded = 1) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    private fun filterByWorkTime(trainings: List<Training>): List<Training> {
        return trainings.filter { training ->
            if (training.isJoined) return@filter true
            val calendar = java.util.Calendar.getInstance()
            calendar.time = java.util.Date(training.startTime * 1000)
            val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            // Calendar.SUNDAY = 1, Calendar.MONDAY = 2, ..., Calendar.SATURDAY = 7
            // We need to map to 0=Monday, 1=Tuesday, ..., 6=Sunday
            val dayIndex = when (dayOfWeek) {
                java.util.Calendar.MONDAY -> 0
                java.util.Calendar.TUESDAY -> 1
                java.util.Calendar.WEDNESDAY -> 2
                java.util.Calendar.THURSDAY -> 3
                java.util.Calendar.FRIDAY -> 4
                java.util.Calendar.SATURDAY -> 5
                java.util.Calendar.SUNDAY -> 6
                else -> 0
            }
            
            // Check if the day is enabled
            val isEnabled = when (dayIndex) {
                0 -> _uiState.value.workTimeMondayEnabled
                1 -> _uiState.value.workTimeTuesdayEnabled
                2 -> _uiState.value.workTimeWednesdayEnabled
                3 -> _uiState.value.workTimeThursdayEnabled
                4 -> _uiState.value.workTimeFridayEnabled
                5 -> _uiState.value.workTimeSaturdayEnabled
                else -> _uiState.value.workTimeSundayEnabled
            }
            
            if (!isEnabled) return@filter true
            
            val workTimes = when (dayIndex) {
                0 -> _uiState.value.workTimeMonday
                1 -> _uiState.value.workTimeTuesday
                2 -> _uiState.value.workTimeWednesday
                3 -> _uiState.value.workTimeThursday
                4 -> _uiState.value.workTimeFriday
                5 -> _uiState.value.workTimeSaturday
                else -> _uiState.value.workTimeSunday
            }

            val startHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val startMinute = calendar.get(java.util.Calendar.MINUTE)
            val startMinutes = startHour * 60 + startMinute
            
            val (workStart, workEnd) = workTimes
            val workStartParts = workStart.split(":")
            val workEndParts = workEnd.split(":")
            
            val workStartHours = workStartParts.getOrNull(0)?.toIntOrNull() ?: 8
            val workStartMins = workStartParts.getOrNull(1)?.toIntOrNull() ?: 0
            val workStartMinutes = workStartHours * 60 + workStartMins
            
            val workEndHours = workEndParts.getOrNull(0)?.toIntOrNull() ?: 18
            val workEndMins = workEndParts.getOrNull(1)?.toIntOrNull() ?: 0
            val workEndMinutes = workEndHours * 60 + workEndMins
            
            startMinutes < workStartMinutes || startMinutes > workEndMinutes
        }
    }

    fun loadNextWeek() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore) return

        // Limit to 4 weeks total
        if (currentState.weeksLoaded >= 4) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, error = null) }

            val nextStartDate = calculateNextWeekStart(currentState.currentStartDate)
            getScheduleUseCase(currentState.selectedLevel, currentState.hideFullyBooked, nextStartDate)
                .onSuccess { newTrainings ->
                    val filteredNew = newTrainings.filter { it.startTime > System.currentTimeMillis() / 1000 }
                    val allTrainings = (currentState.trainings + filteredNew).distinctBy { it.id }
                    _uiState.update { it.copy(isLoadingMore = false, trainings = allTrainings, currentStartDate = nextStartDate, weeksLoaded = currentState.weeksLoaded + 1) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
                }
        }
    }

    private fun calculateNextWeekStart(currentStartDate: String?): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        if (currentStartDate != null) {
            try {
                cal.time = dateFormat.parse(currentStartDate) ?: java.util.Date()
            } catch (e: Exception) { }
        }
        cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
        return dateFormat.format(cal.time)
    }

    fun setLevel(level: SwodLevel) {
        viewModelScope.launch {
            sessionRepository.saveSelectedLevel(level.name)
        }
        loadSchedule(level, _uiState.value.hideFullyBooked)
    }

    fun setHideFullyBooked(hide: Boolean) {
        loadSchedule(_uiState.value.selectedLevel, hide)
    }

    fun bookTraining(training: TrainingEntity) {
        viewModelScope.launch {
            android.util.Log.d("BookTraining", "Starting booking for trainingId: ${training.id}, title: ${training.title}")
            _uiState.update { it.copy(isBookingInProgress = true, bookingError = null) }
            android.util.Log.d("BookTraining", "Calling bookTrainingUseCase for: ${training.id}")
            bookTrainingUseCase(training)
                .onSuccess { booking ->
                    android.util.Log.d("BookTraining", "Booking use case succeeded for: ${booking.trainingId}")
                    val current = _uiState.value.bookings.toMutableList()
                    val bookingWithDetails = booking.copy(
                        instructor = training.instructor,
                        imageUrl = training.imageUrl
                    )
                    current.add(bookingWithDetails)
                    _uiState.update { it.copy(bookings = current) }
                    // Verify booking by refreshing training details
                    android.util.Log.d("BookTraining", "Verifying booking by fetching updated training details for: ${training.id}")
                    getTrainingDetailsUseCase(training.id)
                        .onSuccess { trainingResult ->
                            android.util.Log.d("BookTraining", "Updated training details fetched: isJoined=${trainingResult.isJoined}, spotsAvailable=${trainingResult.spotsAvailable}")
                            
                            if (trainingResult.isJoined) {
                                android.util.Log.d("BookTraining", "Booking verified - user is joined")
                                Toast.makeText(applicationContext, "Successfully booked", Toast.LENGTH_SHORT).show()
                                // Persist booking to DB now that we've verified success
                                val bookingDto = com.swimgym.app.data.model.BookingResponse(
                                    id = 0,
                                    trainingId = training.id,
                                    status = "confirmed",
                                    className = training.title
                                )
                                val booking = bookingDto.toDomain()
                                android.util.Log.d("BookTraining", "Inserting verified booking into DB for: ${training.id}")
                                dao.insertBooking(booking.toEntity())
                                dao.updateTrainingJoined(training.id)
                            } else {
                                android.util.Log.e("BookTraining", "Booking verification failed - user not joined after booking")
                            }
                            
                            _justBookedTrainingId.value = trainingResult.id
                            _selectedTraining.value = trainingResult
                            
                            // update training data with new data and set final state
                            val currentTrainings = _uiState.value.trainings.toMutableList()
                            val index = currentTrainings.indexOfFirst { it.id == trainingResult.id }
                            if (index >= 0) {
                                currentTrainings[index] = trainingResult
                            } else {
                                currentTrainings.add(trainingResult)
                            }
                            
                            val bookingError = if (!trainingResult.isJoined) "Cannot book this training" else null
                            _uiState.update { it.copy(trainings = currentTrainings, isLoading = false, isBookingInProgress = false, bookingError = bookingError) }
                        }
                        .onFailure { e ->
                            android.util.Log.e("BookTraining", "Failed to verify booking details: ${e.message}")
                            _uiState.update { it.copy(isBookingInProgress = false, bookingError = e.message ?: "Booking failed") }
                        }
                }
                .onFailure { e ->
                    android.util.Log.e("BookTraining", "Booking use case failed for ${training.id}: ${e.message}", e)
                    _uiState.update { it.copy(bookingError = e.message ?: "Booking failed", isBookingInProgress = false) }
                }
        }
    }

    fun cancelBooking(booking: Booking) {
        viewModelScope.launch {
            android.util.Log.d("CancelBooking", "Starting cancellation for trainingId: ${booking.trainingId}")
            _uiState.update { it.copy(isCancellingInProgress = true, cancelError = null) }
            val training = Training(
                id = booking.trainingId,
                title = booking.className,
                instructor = booking.instructor,
                startTime = booking.startTime,
                endTime = booking.endTime,
                location = "",
                spotsAvailable = 0,
                imageUrl = booking.imageUrl
            )
            android.util.Log.d("CancelBooking", "Calling cancelBookingUseCase for: ${booking.trainingId}")
            cancelBookingUseCase(training.toEntity())
                .onSuccess {
                    android.util.Log.d("CancelBooking", "Cancellation use case succeeded for: ${booking.trainingId}")
                    val current = _uiState.value.bookings.toMutableList()
                    current.removeAll { it.trainingId == booking.trainingId }
                    _uiState.update { it.copy(bookings = current) }
                    
                    // Remove from calendar
                    val container = SwimGymAppContainer.getInstance()
                    container.calendarService.removeTrainingByTimeAndTitle(booking.startTime, booking.className)
                    android.util.Log.d("CancelBooking", "Removed training from calendar: ${booking.className}")
                    
                    // Verify cancellation by refreshing training details
                    android.util.Log.d("CancelBooking", "Verifying cancellation by fetching updated training details for: ${booking.trainingId}")
                    getTrainingDetailsUseCase(booking.trainingId)
                        .onSuccess { trainingResult ->
                            android.util.Log.d("CancelBooking", "Updated training details fetched: isJoined=${trainingResult.isJoined}, spotsAvailable=${trainingResult.spotsAvailable}")
                            _selectedTraining.value = trainingResult
                            if (!trainingResult.isJoined) {
                                android.util.Log.d("CancelBooking", "Cancellation verified - user is no longer joined")
                                dao.updateTrainingNotJoined(booking.trainingId)
                                // Update the training list with fresh data
                                val currentTrainings = _uiState.value.trainings.toMutableList()
                                val index = currentTrainings.indexOfFirst { it.id == booking.trainingId }
                                if (index >= 0) {
                                    currentTrainings[index] = trainingResult
                                } else {
                                    currentTrainings.add(trainingResult)
                                }
                                _uiState.update { it.copy(bookings = current, trainings = currentTrainings, isCancellingInProgress = false, cancelError = null) }
                            } else {
                                android.util.Log.e("CancelBooking", "Cancellation verification failed - user still joined after cancellation")
                                val updatedCurrent = _uiState.value.bookings.toMutableList()
                                updatedCurrent.add(booking)
                                _uiState.update { it.copy(bookings = updatedCurrent, cancelError = "Cancellation failed", isCancellingInProgress = false) }
                            }
                        }
                        .onFailure { e ->
                            android.util.Log.e("CancelBooking", "Failed to verify cancellation details: ${e.message}")
                            _uiState.update { it.copy(isCancellingInProgress = false, cancelError = e.message ?: "Cancellation verification failed") }
                        }
                }
                .onFailure { e ->
                    android.util.Log.e("CancelBooking", "Cancellation use case failed for ${booking.trainingId}: ${e.message}")
                    _uiState.update { it.copy(cancelError = e.message ?: "Cancellation failed", isCancellingInProgress = false) }
                }
        }
    }

    fun clearBookingError() {
        _uiState.update { it.copy(bookingError = null) }
    }

    fun clearCancelError() {
        _uiState.update { it.copy(cancelError = null) }
    }

    fun clearCalendarError() {
        _uiState.update { it.copy(calendarError = null) }
    }

    fun scheduleRecurringBooking(
        training: Training,
        maxRepeatCount: Int? = null
    ) {
        viewModelScope.launch {
            var locstartTime=training.startTime
            var locinstructor = training.instructor
            var loctitle=training.title
            var loctrainingId=training.id
            val bookingId = System.currentTimeMillis()/1000

            if (training !=null && training!!.isJoined) {
                // if the training is already joined skip this one
                var nexttraining = dao.getTrainingByStartTime(locstartTime+7*86400)
                if (nexttraining != null) {
                    locstartTime=nexttraining.startTime
                    locinstructor=nexttraining.instructor
                    loctitle=nexttraining.title
                    loctrainingId = nexttraining.id
                }
            }
            val scheduledBooking = ScheduledBooking(
                id = bookingId,
                trainingId = loctrainingId,
                className = loctitle,
                startTime = locstartTime,
                instructor = locinstructor,
                maxRepeatCount = maxRepeatCount
            )
            scheduledBookingRepo.saveBooking(scheduledBooking)
            
            val alarmTimeMillis = alarmScheduler.bookingAlarmTimeMillis(scheduledBooking)
            alarmScheduler.scheduleBooking(bookingId, alarmTimeMillis)
        }
    }

    fun getScheduledBookings(): Flow<List<ScheduledBooking>> {
        return scheduledBookingRepo.getAllBookingsFlow()
    }

    fun pauseScheduledBooking(bookingId: Long) {
        viewModelScope.launch {
            scheduledBookingRepo.pauseBooking(bookingId)
            alarmScheduler.cancelBooking(bookingId)
        }
    }

    fun resumeScheduledBooking(bookingId: Long) {
        viewModelScope.launch {
            val booking = scheduledBookingRepo.getBooking(bookingId)
            if (booking != null) {
                scheduledBookingRepo.resumeBooking(bookingId)
                val alarmTimeMillis = alarmScheduler.bookingAlarmTimeMillis(booking)
                alarmScheduler.scheduleBooking(bookingId, alarmTimeMillis)
            }
        }
    }

    fun deleteScheduledBooking(bookingId: Long) {
        viewModelScope.launch {
            alarmScheduler.cancelBooking(bookingId)
            scheduledBookingRepo.deleteBooking(bookingId)
        }
    }

    fun updateMaxRepeat(bookingId: Long, newMax: Int?) {
        viewModelScope.launch {
            scheduledBookingRepo.updateMaxRepeat(bookingId, newMax)
        }
    }

    fun getTrainingById(trainingId: String): Training? {
        return _uiState.value.trainings.find { it.id == trainingId }
    }

    fun loadTrainingDetails(trainingId: String) {
        viewModelScope.launch {
            android.util.Log.d("ScheduleViewModel", "loadTrainingDetails called with trainingId: $trainingId")
            
            // First, load from cache immediately to show data right away
            val cachedTraining = dao.getTrainingById(trainingId)?.toDomain()
            if (cachedTraining != null) {
                android.util.Log.d("ScheduleViewModel", "Loaded cached training details: ${cachedTraining.id} - ${cachedTraining.title}")
                _selectedTraining.value = cachedTraining
            } else {
                android.util.Log.w("ScheduleViewModel", "No cached training found for: $trainingId")
            }
            
            // Now fetch fresh data from API and update
            getTrainingDetailsUseCase(trainingId)
                .onSuccess { training ->
                    android.util.Log.d("ScheduleViewModel", "Successfully fetched updated training details: ${training.id} - ${training.title}")
                    _selectedTraining.value = training
                    // update training data with new data
                    val currentTrainings = _uiState.value.trainings.toMutableList()
                    val index = currentTrainings.indexOfFirst { it.id == trainingId }
                    if (index >= 0) {
                        currentTrainings[index] = training
                    } else {
                        currentTrainings.add(training)
                    }
                    _uiState.update { it.copy(trainings = currentTrainings, isLoading = false) }
                }
                .onFailure { e ->
                    android.util.Log.e("ScheduleViewModel", "Failed to fetch updated training details: $trainingId - ${e.message}")
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun resolveTrainerImage(instructorName: String, remoteImageUrl: String): String {
        if (trainerImageCache.hasLocalImage(instructorName)) {
            val localImage = trainerImageCache.resolveTrainerImageUrl(instructorName, remoteImageUrl)
            if (localImage.isNotEmpty()) return localImage
        }
        // Cache miss, fetch from database in background
        viewModelScope.launch {
            val cachedImage = trainerImageCache.resolveTrainerImageUrlWithCache(instructorName, remoteImageUrl)
            if (cachedImage.isNotEmpty()) {
                // Update the training with cached image
            }
        }
        return remoteImageUrl
    }

    fun refreshSchedule() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            refreshScheduleUseCase()
                .onSuccess {
                    loadSchedule(_uiState.value.selectedLevel, _uiState.value.hideFullyBooked)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
}
