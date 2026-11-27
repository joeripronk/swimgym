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
import com.swimgym.app.util.addDaysToSeconds
import com.swimgym.app.domain.model.Booking
import com.swimgym.app.domain.model.Training
import com.swimgym.app.domain.repository.SwodLevel
import com.swimgym.app.domain.repository.SyncStatus
import com.swimgym.app.domain.usecase.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

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
    val loadMoreError: String? = null,
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
    val calendarError: String? = null,
    val calendarRefreshVersion: Int = 0
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
    private val applicationContext: android.content.Context,
    private val bookingScheduler: com.swimgym.app.data.repository.BookingScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    companion object {
        // Must match the initial fetch window in RepositoryImpl.fetchAndSaveSchedule.
        private const val INITIAL_WEEKS = 4
        private const val MAX_WEEKS = 52
        private val ZONE_ID: ZoneId = ZoneId.systemDefault()
    }

    private var loadScheduleJob: Job? = null

    private val _selectedTraining = MutableStateFlow<Training?>(null)
    val selectedTraining: StateFlow<Training?> = _selectedTraining.asStateFlow()

    private val _justBookedTrainingId = MutableStateFlow<String?>(null)
    val justBookedTrainingId: StateFlow<String?> = _justBookedTrainingId.asStateFlow()

    fun clearJustBookedTrainingId() {
        _justBookedTrainingId.value = null
    }

    fun clearSelectedTraining() {
        _selectedTraining.value = null
    }

    fun clearDialogErrors() {
        _uiState.update { it.copy(bookingError = null, cancelError = null) }
    }

    val imageLoader get() = trainerImageCache.imageLoader

    init {
        viewModelScope.launch {
            val savedLevel = sessionRepository.selectedLevel.first()?.let { levelStr ->
                try { SwodLevel.valueOf(levelStr) } catch (e: Exception) { SwodLevel.ALL }
            } ?: SwodLevel.ALL
            _uiState.update { it.copy(selectedLevel = savedLevel) }
            loadSchedule(savedLevel, _uiState.value.hideFullyBooked)
        }

        viewModelScope.launch {
            sessionRepository.selectedLevel.collect { savedLevel ->
                val level = savedLevel?.let { levelStr ->
                    try { SwodLevel.valueOf(levelStr) } catch (e: Exception) { SwodLevel.ALL }
                } ?: SwodLevel.ALL
                if (level != _uiState.value.selectedLevel) {
                    _uiState.update { it.copy(selectedLevel = level) }
                    loadSchedule(level, _uiState.value.hideFullyBooked)
                }
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
        hideFullyBooked: Boolean = _uiState.value.hideFullyBooked,
        preserveError: String? = null
    ) {
        loadScheduleJob?.cancel()
        loadScheduleJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true, error = preserveError, loadMoreError = null,
                    isLoadingMore = false, currentStartDate = null, weeksLoaded = 1
                )
            }
            getScheduleUseCase(level, hideFullyBooked, null)
                .onSuccess { trainings ->
                    var filteredTrainings = trainings.filter { it.startTime > System.currentTimeMillis()/1000 }

                    // Apply work time filter if enabled
                    if (_uiState.value.workTimeFilterEnabled) {
                        filteredTrainings = filterByWorkTime(filteredTrainings)
                    }

                    // The refresh prefetches a fixed window of weeks. Anchor the
                    // lazy loader at the last of those weeks so the next page
                    // fetches the week after it, not one already cached.
                    val lastInitialMonday = LocalDate.now(ZONE_ID)
                        .with(DayOfWeek.MONDAY)
                        .plusWeeks((INITIAL_WEEKS - 2).toLong())
                        .toString()
                    _uiState.update {
                        it.copy(
                            isLoading = false, trainings = filteredTrainings, selectedLevel = level,
                            hideFullyBooked = hideFullyBooked, currentStartDate = lastInitialMonday,
                            weeksLoaded = INITIAL_WEEKS, error = preserveError
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: preserveError) }
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
            
            val enabledDays = listOf(
                _uiState.value.workTimeMondayEnabled,
                _uiState.value.workTimeTuesdayEnabled,
                _uiState.value.workTimeWednesdayEnabled,
                _uiState.value.workTimeThursdayEnabled,
                _uiState.value.workTimeFridayEnabled,
                _uiState.value.workTimeSaturdayEnabled,
                _uiState.value.workTimeSundayEnabled
            )
            val isEnabled = enabledDays.getOrElse(dayIndex) { _uiState.value.workTimeMondayEnabled }

            if (!isEnabled) return@filter true

            val workTimesList = listOf(
                _uiState.value.workTimeMonday,
                _uiState.value.workTimeTuesday,
                _uiState.value.workTimeWednesday,
                _uiState.value.workTimeThursday,
                _uiState.value.workTimeFriday,
                _uiState.value.workTimeSaturday,
                _uiState.value.workTimeSunday
            )
            val workTimes = workTimesList.getOrElse(dayIndex) { _uiState.value.workTimeMonday }

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
        if (currentState.isLoadingMore || currentState.isLoading) return

        // Guard against runaway fetching when the list is shorter than the
        // viewport and the scroll trigger stays true.
        if (currentState.weeksLoaded >= MAX_WEEKS) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }

            val nextStartDate = calculateNextWeekStart(currentState.currentStartDate)
            getScheduleUseCase(currentState.selectedLevel, currentState.hideFullyBooked, nextStartDate)
                .onSuccess { newTrainings ->
                    var filteredNew = newTrainings.filter { it.startTime > System.currentTimeMillis() / 1000 }
                    if (currentState.workTimeFilterEnabled) {
                        filteredNew = filterByWorkTime(filteredNew)
                    }
                    val allTrainings = (currentState.trainings + filteredNew).distinctBy { it.id }
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false, trainings = allTrainings,
                            currentStartDate = nextStartDate, weeksLoaded = currentState.weeksLoaded + 1
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoadingMore = false, loadMoreError = e.message ?: "Failed to load more weeks")
                    }
                }
        }
    }

    private fun calculateNextWeekStart(currentStartDate: String?): String {
        val base = currentStartDate?.let {
            try { LocalDate.parse(it) } catch (e: Exception) { null }
        } ?: LocalDate.now(ZONE_ID).with(DayOfWeek.MONDAY)
        return base.plusWeeks(1).with(DayOfWeek.MONDAY).toString()
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
                            _uiState.update { it.copy(trainings = currentTrainings, isLoading = false, isBookingInProgress = false, bookingError = bookingError, calendarRefreshVersion = _uiState.value.calendarRefreshVersion + 1) }
                        }
                        .onFailure { e ->
                            android.util.Log.e("BookTraining", "Failed to verify booking details: ${e.message}")
                            _uiState.update { it.copy(isBookingInProgress = false, bookingError = e.message ?: "Booking failed", calendarRefreshVersion = _uiState.value.calendarRefreshVersion + 1) }
                        }
                }
                .onFailure { e ->
                    android.util.Log.e("BookTraining", "Booking use case failed for ${training.id}: ${e.message}", e)
                    _uiState.update { it.copy(bookingError = e.message ?: "Booking failed", isBookingInProgress = false, calendarRefreshVersion = _uiState.value.calendarRefreshVersion + 1) }
                }
        }
    }

    fun cancelBooking(booking: Booking) {
        viewModelScope.launch {
            android.util.Log.d("CancelBooking", "Starting cancellation for trainingId: ${booking.trainingId}")
            _uiState.update { it.copy(isCancellingInProgress = true, cancelError = null) }
            // The Booking domain model carries no classTime/classDate, but the
            // cancel form POST requires them. Rebuild from the cached entity so
            // the server receives the real values instead of empty strings.
            val cached = dao.getTrainingById(booking.trainingId)
            val training = Training(
                id = booking.trainingId,
                title = booking.className,
                instructor = booking.instructor,
                startTime = booking.startTime,
                endTime = booking.endTime,
                location = cached?.location ?: "",
                spotsAvailable = cached?.spotsAvailable ?: 0,
                classTime = cached?.classTime ?: "",
                classDate = cached?.classDate ?: "",
                imageUrl = booking.imageUrl.ifEmpty { cached?.imageUrl ?: "" }
            )
            android.util.Log.d("CancelBooking", "Calling cancelBookingUseCase for: ${booking.trainingId}")
            cancelBookingUseCase(training.toEntity())
                .onSuccess {
                    android.util.Log.d("CancelBooking", "Cancellation use case succeeded for: ${booking.trainingId}")
                    val current = _uiState.value.bookings.toMutableList()
                    current.removeAll { it.trainingId == booking.trainingId }
                    _uiState.update { it.copy(bookings = current) }
                    
                    android.util.Log.d("CancelBooking", "Cancellation verified for: ${booking.trainingId}")
                    dao.updateTrainingNotJoined(booking.trainingId)
                    val currentTrainings = _uiState.value.trainings.toMutableList()
                    val index = currentTrainings.indexOfFirst { it.id == booking.trainingId }
                    if (index >= 0) {
                        currentTrainings[index] = training
                    } else {
                        currentTrainings.add(training)
                    }
                    _selectedTraining.value = _selectedTraining.value?.copy(isJoined = false)
                    _uiState.update { it.copy(bookings = current, trainings = currentTrainings, isCancellingInProgress = false, cancelError = null, calendarRefreshVersion = _uiState.value.calendarRefreshVersion + 1) }
                    // Refresh only after cancellation completes, otherwise the detail
                    // reload can read the still-joined state from the server.
                    loadTrainingDetails(booking.trainingId)
                }
                .onFailure { e ->
                    android.util.Log.e("CancelBooking", "Cancellation use case failed for ${booking.trainingId}: ${e.message}")
                    _uiState.update { it.copy(cancelError = e.message ?: "Cancellation failed", isCancellingInProgress = false, calendarRefreshVersion = _uiState.value.calendarRefreshVersion + 1) }
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
            val bookingId = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE

            if (training.isJoined) {
                // if the training is already joined skip this one
                var nexttraining = dao.getTrainingByStartTime(addDaysToSeconds(locstartTime, 7))
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

    fun stopRetrying(bookingId: Long) {
        viewModelScope.launch {
            bookingScheduler.stopRetries(bookingId)
        }
    }

    fun updateMaxRepeat(bookingId: Long, newMax: Int?) {
        viewModelScope.launch {
            scheduledBookingRepo.updateMaxRepeat(bookingId, newMax)
        }
    }

    private val trainingMap: StateFlow<Map<String, Training>> = _uiState
        .map { it.trainings.associateBy { t -> t.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun getTrainingById(trainingId: String): Training? {
        return trainingMap.value[trainingId]
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
                    // Refresh bookings to reflect updated joined state
                    val currentBookings = getMyBookingsUseCase().first()
                    _uiState.update { it.copy(trainings = currentTrainings, bookings = currentBookings, isLoading = false) }
                }
                .onFailure { e ->
                    android.util.Log.e("ScheduleViewModel", "Failed to fetch updated training details: $trainingId - ${e.message}")
                    // Keep the cached training on screen. Writing to the shared
                    // error would replace the whole schedule with an error once
                    // the user navigates back, even though the cache is valid.
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    fun resolveTrainerImage(instructorName: String, remoteImageUrl: String): String =
        trainerImageCache.resolveTrainerImageUrl(instructorName, remoteImageUrl)

    fun refreshSchedule() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            refreshScheduleUseCase()
                .onSuccess {
                    loadSchedule(_uiState.value.selectedLevel, _uiState.value.hideFullyBooked)
                    bookingScheduler.checkCalendarConflicts()
                }
                .onFailure { e ->
                    // A partial refresh persisted some weeks; show them and keep
                    // the refresh error visible instead of leaving an empty screen.
                    loadSchedule(
                        level = _uiState.value.selectedLevel,
                        hideFullyBooked = _uiState.value.hideFullyBooked,
                        preserveError = e.message
                    )
                }
        }
    }
}
