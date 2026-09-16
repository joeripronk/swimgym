package com.swimgym.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swimgym.app.di.SwimGymAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ScheduledBookingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bookingId = intent.getLongExtra("booking_id", -1L)
        if (bookingId == -1L) return

        val container = SwimGymAppContainer.getInstance()
        val webScraper = container.webScraper

        runBlocking {
            try {
                webScraper.processSingleBooking(bookingId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
