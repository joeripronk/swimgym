package com.swimgym.app.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swimgym.app.di.SwimGymAppContainer
import com.swimgym.app.data.api.WebScraper
import com.swimgym.app.data.model.Mappers.toEntity
import com.swimgym.app.domain.model.BookingStatus
import com.swimgym.app.receiver.LoginActivity
import com.swimgym.app.ui.screens.LoginScreen
import com.swimgym.app.ui.screens.MyBookingsScreen
import com.swimgym.app.ui.screens.ScheduleScreen
import com.swimgym.app.ui.screens.TrainingDetailScreen
import com.swimgym.app.ui.screens.SettingsScreen
import com.swimgym.app.ui.viewmodel.ScheduleViewModel
import com.swimgym.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.internal.toLongOrDefault

@ExperimentalComposeUiApi
@Composable
fun SwimGymNavigation(
    context: Context,
    webScraper: WebScraper
) {
    val navController = rememberNavController()
    val container = SwimGymAppContainer.getInstance()
    
    var hasCookies by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        val cookies = container.sessionRepository.loadCookies()
        var lid: Long = 0
        if (cookies.contains("virtuagym_u")) {
            val uid = cookies.get("virtuagym_u")
            lid = uid?.toLongOrDefault(1L)!!
        }
        hasCookies =  lid>1

    }
    
    LaunchedEffect(Unit) {
        if ((context as? Activity)?.intent?.action == LoginActivity.ACTION_LOGIN_REQUIRED) {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Schedule.route) { inclusive = true }
            }
        }
    }
    
    val scheduleViewModel = ScheduleViewModel(
        getScheduleUseCase = container.getScheduleUseCase,
        getTrainingDetailsUseCase = container.getTrainingDetailsUseCase,
        getMyBookingsUseCase = container.getMyBookingsUseCase,
        bookTrainingUseCase = container.bookTrainingUseCase,
        cancelBookingUseCase = container.cancelBookingUseCase,
        refreshScheduleUseCase = container.refreshScheduleUseCase,
        getSyncStatusUseCase = container.getSyncStatusUseCase,
        sessionRepository = container.sessionRepository,
        scheduledBookingRepo = container.scheduledBookingRepository,
        trainerImageCache = container.trainerImageCache,
        dao = container.dao,
        applicationContext = context
    )
    
    val settingsViewModel = SettingsViewModel(
        sessionRepository = container.sessionRepository,
        calendarRepository = container.calendarRepository
    )

    NavHost(
        navController = navController,
        startDestination = if (hasCookies) Screen.Schedule.route else Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                webScraper = webScraper,
                onLoginSuccess = {
                    scheduleViewModel.refreshSchedule()
                    navController.navigate(Screen.Schedule.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Schedule.route) {
            LaunchedEffect(Unit) {
                if (hasCookies) {
                    scheduleViewModel.loadSchedule()
                }
            }
            ScheduleScreen(
                onTrainingClick = { training ->
                    navController.navigate(Screen.TrainingDetail.createRoute(training.id))
                },
                onMyBookingsClick = {
                    navController.navigate(Screen.MyBookings.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onLogout = {
                    runBlocking {
                        webScraper.logout()
                    }
                    hasCookies = false
                    val intent = Intent(LoginActivity.ACTION_LOGIN_REQUIRED)
                    context.sendBroadcast(intent)
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                viewModel = scheduleViewModel
            )
        }

        composable(
            route = Screen.TrainingDetail.route,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType }
            )
         ) { backStackEntry ->
            val encodedId = backStackEntry.arguments?.getString("id") ?: return@composable
            val id = Screen.decodeId(encodedId)
            LaunchedEffect(id) {
                scheduleViewModel.loadTrainingDetails(id)
            }
            //val selectedTraining by scheduleViewModel.selectedTraining.collectAsState(initial = null)
            var selectedTraining = scheduleViewModel.getTrainingById(trainingId = id)

            val uiState by scheduleViewModel.uiState.collectAsState()
            val isLoading = uiState.isLoading
            val justBookedTrainingId by scheduleViewModel.justBookedTrainingId.collectAsState()
            val trainerImageUrl = selectedTraining?.let { training ->
                scheduleViewModel.resolveTrainerImage(training.instructor, training.imageUrl)
            }
            if (selectedTraining!=null && trainerImageUrl!=null) {
                selectedTraining.imageUrl = trainerImageUrl
            }
            // Close view if booking was just verified
            LaunchedEffect(justBookedTrainingId) {
                if (justBookedTrainingId == id) {
                    scheduleViewModel.clearJustBookedTrainingId()
                   // navController.popBackStack()
                }
            }

            TrainingDetailScreen(
                 training = selectedTraining,
                 onBack = { navController.popBackStack() },
                 onBook = { training ->
                    scheduleViewModel.bookTraining(training.toEntity())
                },
                onCancel = { training ->
                    val booking = com.swimgym.app.domain.model.Booking(
                        id = 0,
                        trainingId = training.id,
                        className = training.title,
                        startTime = training.startTime,
                        endTime = training.endTime,
                        status = BookingStatus.BOOKED
                    )
                    scheduleViewModel.cancelBooking(booking)
                },
                onSchedule = { trainingId, title, instructor, maxRepeat ->
                    scheduleViewModel.scheduleRecurringBooking(
                        trainingId = trainingId,
                        title = title,
                        startTime = selectedTraining?.startTime ?: 0L,
                        instructor = instructor,
                        maxRepeatCount = maxRepeat
                    )
                },
                onAddToCalendar = { training ->
                },
                onNavigateToMyBookings = {
                    navController.navigate(Screen.MyBookings.route)
                },
                onRefresh = { trainingId ->
                    scheduleViewModel.loadTrainingDetails(trainingId)
                },
                onBookingSuccess = { trainingId ->
                    scheduleViewModel.loadTrainingDetails(trainingId)
                },
                onCancellationSuccess = { trainingId ->
                    scheduleViewModel.loadTrainingDetails(trainingId)
                },
                isBookingInProgress = uiState.isBookingInProgress,
                isCancellingInProgress = uiState.isCancellingInProgress,
                bookingError = uiState.bookingError,
                cancelError = uiState.cancelError,
                onClearBookingError = {
                    scheduleViewModel.clearBookingError()
                },
                onClearCancelError = {
                    scheduleViewModel.clearCancelError()
                }
            )
        }

        composable(Screen.MyBookings.route) {
            val scheduleState by scheduleViewModel.uiState.collectAsState()
            val scheduledBookingsFlow = scheduleViewModel.getScheduledBookings()
            val scheduledBookings by scheduledBookingsFlow.collectAsState(initial = emptyList())

            MyBookingsScreen(
                bookings = scheduleState.bookings,
                scheduledBookings = scheduledBookings,
                onBack = { navController.popBackStack() },
                onCancelBooking = { booking ->
                    scheduleViewModel.cancelBooking(booking)
                },
                onPauseScheduled = { bookingId ->
                    scheduleViewModel.pauseScheduledBooking(bookingId)
                },
                onResumeScheduled = { bookingId ->
                    scheduleViewModel.resumeScheduledBooking(bookingId)
                },
                onDeleteScheduled = { bookingId ->
                    scheduleViewModel.deleteScheduledBooking(bookingId)
                },
                onScheduledBookingClick = { trainingId ->
                    navController.navigate(Screen.TrainingDetail.createRoute(trainingId))
                },
                onBookingClick = { booking ->
                    navController.navigate(Screen.TrainingDetail.createRoute(booking.trainingId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}