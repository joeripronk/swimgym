@file:OptIn(ExperimentalComposeUiApi::class)

package com.swimgym.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.swimgym.app.di.SwimGymAppContainer
import com.swimgym.app.receiver.LoginActivity
import com.swimgym.app.ui.navigation.SwimGymNavigation
import com.swimgym.app.ui.theme.SwimGymTheme

class MainActivity : ComponentActivity() {

    private val _loginRequiredCount = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.action == LoginActivity.ACTION_LOGIN_REQUIRED) {
            _loginRequiredCount.value += 1
        }
        setContent {
            val loginRequiredCount by _loginRequiredCount.collectAsState()
            SwimGymTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SwimGymNavigation(
                        context = this,
                        loginRequiredCount = loginRequiredCount
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CoroutineScope(Dispatchers.IO).launch {
            SwimGymAppContainer.getInstance().bookingScheduler.checkCalendarConflicts()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == LoginActivity.ACTION_LOGIN_REQUIRED) {
            _loginRequiredCount.value += 1
        }
    }
}