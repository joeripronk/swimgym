package com.swimgym.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swimgym.app.data.api.WebScraper
import com.swimgym.app.data.model.Mappers.toEntity
import com.swimgym.app.ui.navigation.Screen
import com.swimgym.app.data.repository.ScheduledBooking
import com.swimgym.app.domain.model.BookingStatus
import com.swimgym.app.ui.screens.LoginScreen
import com.swimgym.app.ui.screens.MyBookingsScreen
import com.swimgym.app.ui.screens.ScheduleScreen
import com.swimgym.app.ui.screens.TrainingDetailScreen
import com.swimgym.app.ui.screens.SettingsScreen
import com.swimgym.app.ui.viewmodel.LoginViewModel
import com.swimgym.app.ui.viewmodel.ScheduleViewModel
import com.swimgym.app.ui.viewmodel.SettingsViewModel

@ExperimentalComposeUiApi
@Composable
fun SwimGymNavigation(
    webScraper: WebScraper
) {
    val navController = rememberNavController()
    val scheduleViewModel: ScheduleViewModel = hiltViewModel()
    val loginViewModel: LoginViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val scheduleState by scheduleViewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                webScraper = webScraper,
                onLoginSuccess = {
                    scheduleViewModel.loadSchedule()
                    navController.navigate(Screen.Schedule.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Schedule.route) {
            ScheduleScreen(
                viewModel = scheduleViewModel,
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
                    loginViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
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
            val selectedTraining by scheduleViewModel.selectedTraining.collectAsState()
            val justBookedTrainingId by scheduleViewModel.justBookedTrainingId.collectAsState()
            val trainerImageUrl = selectedTraining?.let { training ->
                scheduleViewModel.resolveTrainerImage(training.instructor, training.imageUrl)
            }

            // Close view if booking was just verified
            LaunchedEffect(justBookedTrainingId) {
                if (justBookedTrainingId == id) {
                    scheduleViewModel.clearJustBookedTrainingId()
                    navController.popBackStack()
                }
            }

            TrainingDetailScreen(
                trainingId = id,
                trainings = listOfNotNull(selectedTraining),
                trainerImageUrl = trainerImageUrl,
                onBack = { navController.popBackStack() },
                onBook = { training ->
                    scheduleViewModel.bookTraining(training.toEntity())
                },
                onCancel = { training ->
                    val booking = com.swimgym.app.domain.model.Booking(
                        id = training.id.hashCode(),
                        trainingId = training.id,
                        className = training.title,
                        classTime = training.classTime,
                        classDate = training.classDate,
                        status = BookingStatus.BOOKED
                    )
                    scheduleViewModel.cancelBooking(booking)
                },
                onSchedule = { trainingId, className, classTime, classDate, maxRepeat ->
                    scheduleViewModel.scheduleRecurringBooking(
                        trainingId = trainingId,
                        className = className,
                        classTime = classTime,
                        classDate = classDate,
                        startTime = selectedTraining?.startTime ?: 0L,
                        instructor = selectedTraining?.instructor ?: "",
                        maxRepeatCount = maxRepeat
                    )
                }
            )
        }

        composable(Screen.MyBookings.route) {
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