package com.swimgym.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.swimgym.app.data.api.WebScraper
import com.swimgym.app.ui.navigation.SwimGymNavigation
import com.swimgym.app.ui.theme.SwimGymTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var webScraper: WebScraper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwimGymTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SwimGymNavigation(webScraper = webScraper)
                }
            }
        }
    }
}