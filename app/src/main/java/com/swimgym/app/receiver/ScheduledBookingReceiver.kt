package com.swimgym.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swimgym.app.di.SwimGymAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScheduledBookingReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_RETRY_BOOKING = "com.swimgym.app.RETRY_BOOKING"
        const val ACTION_STOP_RETRY_BOOKING = "com.swimgym.app.STOP_RETRY_BOOKING"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val bookingId = intent.getLongExtra("booking_id", -1L)
        if (bookingId == -1L) return

        val pendingResult = goAsync()
        val container = SwimGymAppContainer.getInstance()

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                if (intent.action == ACTION_STOP_RETRY_BOOKING) {
                    container.bookingScheduler.stopRetries(bookingId)
                } else {
                    container.bookingScheduler.processSingleBooking(bookingId)
                }
            } catch (e: Exception) {
                android.util.Log.e("ScheduledBookingReceiver", "Booking action failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
