package com.swimgym.app.ui.viewmodel

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.model.Mappers.toEntity
import com.swimgym.app.data.repository.ScheduledBooking
import com.swimgym.app.data.repository.ScheduledBookingRepository
import com.swimgym.app.data.repository.TrainerImageCache
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
    val cancelError: String? = null
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
            _uiState.update { it.copy(isBookingInProgress = true, bookingError = null) }
            bookTrainingUseCase(training)
                .onSuccess { booking ->
                    val current = _uiState.value.bookings.toMutableList()
                    val bookingWithDetails = booking.copy(
                        instructor = training.instructor,
                        imageUrl = training.imageUrl
                    )
                    current.add(bookingWithDetails)
                    _uiState.update { it.copy(bookings = current) }
                    // Verify booking by refreshing training details
                    getTrainingDetailsUseCase(training.id)
                        .onSuccess { training ->
                            _justBookedTrainingId.value = training.id
                            _selectedTraining.value = training
                            // update training data with new data
                            val currentTrainings = _uiState.value.trainings.toMutableList()
                            val index = currentTrainings.indexOfFirst { it.id == training.id }
                            if (index >= 0) {
                                currentTrainings[index] = training
                            } else {
                                currentTrainings.add(training)
                            }
                            if (training.isJoined) {
                                Toast.makeText(applicationContext,"Successfully booked",5)
                            } else {
                                _uiState.update { it.copy(bookingError = "Cannot book this training") }
                            }
                            _uiState.update { it.copy(trainings = currentTrainings, isLoading = false, isBookingInProgress = false) }
                        }
                        .onFailure { e ->
                            _uiState.update { it.copy(isBookingInProgress = false, bookingError = e.message ?: "Booking failed") }
                        }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(bookingError = e.message ?: "Booking failed", isBookingInProgress = false) }
                }
        }
    }

    fun cancelBooking(booking: Booking) {
        viewModelScope.launch {
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
            cancelBookingUseCase(training.toEntity())
                .onSuccess {
                    val current = _uiState.value.bookings.toMutableList()
                    current.removeAll { it.trainingId == booking.trainingId }
                    _uiState.update { it.copy(bookings = current, cancelError = null, isCancellingInProgress = false) }
                }
                .onFailure { e ->
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

    fun scheduleRecurringBooking(
        trainingId: String,
        title: String,
        startTime: Long = 0L,
        instructor: String = "",
        maxRepeatCount: Int? = null
    ) {
        viewModelScope.launch {
            var locstartTime=startTime
            var locinstructor = instructor
            var loctitle=title
            var loctrainingId=trainingId
            val bookingId = System.currentTimeMillis()/1000
            var training = dao.getTrainingById(trainingId)
            if (training !=null && training!!.isJoined) {
                training = dao.getTrainingByStartTime(startTime+7*86400)
                if (training != null) {
                    locstartTime+=7*86400
                    locinstructor=training.instructor
                    loctitle=training.title
                    loctrainingId = training.id
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
            // Periodic worker will pick up and process this booking
        }
    }

    fun getScheduledBookings(): Flow<List<ScheduledBooking>> {
        return scheduledBookingRepo.getAllBookingsFlow()
    }

    fun pauseScheduledBooking(bookingId: Long) {
        viewModelScope.launch {
            scheduledBookingRepo.pauseBooking(bookingId)
        }
    }

    fun resumeScheduledBooking(bookingId: Long) {
        viewModelScope.launch {
            scheduledBookingRepo.resumeBooking(bookingId)
        }
    }

    fun deleteScheduledBooking(bookingId: Long) {
        viewModelScope.launch {
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
            _uiState.update { it.copy(isLoading = true, error = null, bookingError = null, cancelError = null) }
            getTrainingDetailsUseCase(trainingId)
                .onSuccess { training ->
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
