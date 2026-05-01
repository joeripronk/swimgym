package com.swimgym.app.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Schedule : Screen("schedule")
    object MyBookings : Screen("myBookings")
    object TrainingDetail : Screen("trainingDetail/{id}") {
        fun createRoute(id: String) = "trainingDetail/${id.replace("/", "__")}"
    }
    object Settings : Screen("settings")

    companion object {
        fun decodeId(encodedId: String): String = encodedId.replace("__", "/")
    }
}
