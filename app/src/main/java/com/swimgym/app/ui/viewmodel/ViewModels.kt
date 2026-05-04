package com.swimgym.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.model.Mappers.toEntity
import com.swimgym.app.data.repository.ScheduledBooking
import com.swimgym.app.data.repository.ScheduledBookingRepository
import com.swimgym.app.data.repository.ScheduledBookingStatus
import com.swimgym.app.data.repository.TrainerImageCache
import com.swimgym.app.domain.model.Booking
import com.swimgym.app.domain.model.Training
import com.swimgym.app.domain.repository.SwodLevel
import com.swimgym.app.domain.repository.SyncStatus
import com.swimgym.app.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val isLoggedInUseCase: IsLoggedInUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun login() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val state = _uiState.value
            loginUseCase(state.email, state.password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
        }
    }
}

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
    val syncStatus: SyncStatus = SyncStatus()
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val getScheduleUseCase: GetScheduleUseCase,
    private val getTrainingDetailsUseCase: GetTrainingDetailsUseCase,
    private val getMyBookingsUseCase: GetMyBookingsUseCase,
    private val bookTrainingUseCase: BookTrainingUseCase,
    private val cancelBookingUseCase: CancelBookingUseCase,
    private val refreshScheduleUseCase: RefreshScheduleUseCase,
    private val getSyncStatusUseCase: GetSyncStatusUseCase,
    private val sessionRepository: com.swimgym.app.data.repository.SessionRepository,
    private val scheduledBookingRepo: ScheduledBookingRepository,
    private val trainerImageCache: TrainerImageCache,
    private val dao: com.swimgym.app.data.local.SwimGymDao,
    @ApplicationContext private val applicationContext: android.content.Context
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
        viewModelScope.launch {
            sessionRepository.selectedLevel.collect { savedLevel ->
                val level = savedLevel?.let { levelStr ->
                    try { com.swimgym.app.domain.repository.SwodLevel.valueOf(levelStr) } catch (e: Exception) { com.swimgym.app.domain.repository.SwodLevel.ALL }
                } ?: com.swimgym.app.domain.repository.SwodLevel.ALL
                _uiState.update { it.copy(selectedLevel = level) }
                loadSchedule(level)
            }
        }
        observeBookings()
        observeSyncStatus()
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            getSyncStatusUseCase().collect { syncStatus: SyncStatus ->
                _uiState.update { it.copy(syncStatus = syncStatus) }
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
                    val filteredTrainings = trainings.filter { it.startTime > System.currentTimeMillis()/1000 }
                    _uiState.update { it.copy(isLoading = false, trainings = filteredTrainings, selectedLevel = level, hideFullyBooked = hideFullyBooked, currentStartDate = null, weeksLoaded = 1) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
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
                    val filteredNew = newTrainings.filter { it.startTime > System.currentTimeMillis() }
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
            bookTrainingUseCase(training,applicationContext)
                .onSuccess { booking ->
                    val current = _uiState.value.bookings.toMutableList()
                    current.add(booking)
                    _uiState.update { it.copy(bookings = current) }
                    // Verify booking by refreshing training details
                    getTrainingDetailsUseCase(training.id)
                        .onSuccess { updatedTraining ->
                            _selectedTraining.value = updatedTraining
                            _justBookedTrainingId.value = training.id
                        }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun cancelBooking(booking: Booking) {
        viewModelScope.launch {
            val training = Training(
                id = booking.trainingId,
                title = booking.className,
                instructor = "",
                startTime = 0L,
                endTime = 0L,
                location = "",
                spotsAvailable = 0,
                classTime = booking.classTime,
                classDate = booking.classDate
            )
            cancelBookingUseCase(training.toEntity(),applicationContext)
                .onSuccess {
                    val current = _uiState.value.bookings.toMutableList()
                    current.removeAll { it.trainingId == booking.trainingId }
                    _uiState.update { it.copy(bookings = current) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun scheduleRecurringBooking(
        trainingId: String,
        className: String,
        classTime: String,
        classDate: String,
        startTime: Long = 0L,
        instructor: String = "",
        maxRepeatCount: Int? = null
    ) {
        viewModelScope.launch {
            val bookingId = java.util.UUID.randomUUID().toString()
            val scheduledBooking = ScheduledBooking(
                id = bookingId,
                trainingId = trainingId,
                className = className,
                classTime = classTime,
                classDate = classDate,
                startTime = startTime,
                instructor = instructor,
                maxRepeatCount = maxRepeatCount
            )
            scheduledBookingRepo.saveBooking(scheduledBooking)
            // Periodic worker will pick up and process this booking
        }
    }

    fun getScheduledBookings(): Flow<List<ScheduledBooking>> {
        return scheduledBookingRepo.getAllBookingsFlow()
    }

    fun pauseScheduledBooking(bookingId: String) {
        viewModelScope.launch {
            scheduledBookingRepo.pauseBooking(bookingId)
        }
    }

    fun resumeScheduledBooking(bookingId: String) {
        viewModelScope.launch {
            scheduledBookingRepo.resumeBooking(bookingId)
        }
    }

    fun deleteScheduledBooking(bookingId: String) {
        viewModelScope.launch {
            scheduledBookingRepo.deleteBooking(bookingId)
        }
    }

    fun updateMaxRepeat(bookingId: String, newMax: Int?) {
        viewModelScope.launch {
            scheduledBookingRepo.updateMaxRepeat(bookingId, newMax)
        }
    }

    fun loadTrainingDetails(trainingId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            getTrainingDetailsUseCase(trainingId)
                .onSuccess { training ->
                    _selectedTraining.value = training
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun resolveTrainerImage(instructorName: String, remoteImageUrl: String): String {
        if (trainerImageCache.hasLocalImage(instructorName)) {
            //     trainerImageCache.resolveTrainerImageUrl(instructorName, remoteImageUrl)
            //}
            val localImage =
                trainerImageCache.resolveTrainerImageUrl(instructorName, remoteImageUrl)
            if (localImage.isNotEmpty() || remoteImageUrl.isEmpty()) return localImage
        }
        // Cache miss, trigger background fetch
        viewModelScope.launch {
            val instructor = dao.getInstructor(instructorName)
            if (instructor?.instructorImage?.isNotEmpty() == true) {
                trainerImageCache.resolveTrainerImageUrl(instructorName, instructor.instructorImage)
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
