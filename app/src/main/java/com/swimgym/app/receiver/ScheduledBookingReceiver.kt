package com.swimgym.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swimgym.app.di.SwimGymAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ScheduledBookingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bookingId = intent.getLongExtra("booking_id", -1L)
        if (bookingId == -1L) return

        val pendingResult = goAsync()
        val container = SwimGymAppContainer.getInstance()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.bookingScheduler.processSingleBooking(bookingId)
            } catch (e: Exception) {
                android.util.Log.e("ScheduledBookingReceiver", "Booking failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
