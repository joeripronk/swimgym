@file:OptIn(ExperimentalComposeUiApi::class)

package com.swimgym.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import com.swimgym.app.di.SwimGymAppContainer
import com.swimgym.app.ui.navigation.SwimGymNavigation
import com.swimgym.app.ui.theme.SwimGymTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val webScraper = SwimGymAppContainer.getInstance().webScraper
        setContent {
            SwimGymTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SwimGymNavigation(context = this, webScraper = webScraper)
                }
            }
        }
    }
}