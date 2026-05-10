package com.swimgym.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swimgym.app.MainActivity

class LoginRequiredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == LoginActivity.ACTION_LOGIN_REQUIRED) {
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                setAction(LoginActivity.ACTION_LOGIN_REQUIRED)
            }
            context.startActivity(mainIntent)
        }
    }
}

object LoginActivity {
    const val ACTION_LOGIN_REQUIRED = "com.swimgym.app.LOGIN_REQUIRED"
}
