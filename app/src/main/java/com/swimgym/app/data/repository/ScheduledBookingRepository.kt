package com.swimgym.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.swimgym.app.data.local.entity.TrainingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.scheduledBookingDataStore by preferencesDataStore(name = "scheduled_bookings")

class ScheduledBookingRepository(
    private val context: Context
) {
    private val bookingsKey = stringPreferencesKey("scheduled_bookings_list")

    suspend fun saveBooking(booking: ScheduledBooking) {
        val current = getAllBookings().toMutableList()
        current.removeAll { it.id == booking.id }
        current.add(booking)
        saveAllBookings(current)
    }

    suspend fun getAllBookings(): List<ScheduledBooking> {
        return try {
            val prefs = context.scheduledBookingDataStore.data.first()
            val json = prefs[bookingsKey] ?: "[]"
            // Simple JSON parsing without Gson
            parseBookingsFromJson(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAllBookingsFlow(): Flow<List<ScheduledBooking>> {
        return context.scheduledBookingDataStore.data.map { prefs ->
            try {
                val json = prefs[bookingsKey] ?: "[]"
                parseBookingsFromJson(json)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getBooking(id: Long): ScheduledBooking? {
        return getAllBookings().find { it.id == id }
    }

    suspend fun incrementBookingCount(bookingId: Long, training: TrainingEntity) {
        val booking = getBooking(bookingId) ?: return
        saveBooking(booking.copy(
            bookedCount = booking.bookedCount + 1,
	    // the only verified source for starttime is from training
	    startTime = training.startTime + 7 * 86400,
            instructor = training.instructor,
            trainingId = training.id
        ))
    }

    suspend fun markBookingAsInvalid(bookingId: Long) {
        val booking = getBooking(bookingId) ?: return
        saveBooking(booking.copy(status = ScheduledBookingStatus.INVALIDATED))
    }

    suspend fun completeBooking(bookingId: Long) {
        val booking = getBooking(bookingId) ?: return
        saveBooking(booking.copy(status = ScheduledBookingStatus.COMPLETED))
    }

    suspend fun pauseBooking(bookingId: Long) {
        val booking = getBooking(bookingId) ?: return
        saveBooking(booking.copy(status = ScheduledBookingStatus.PAUSED))
    }

    suspend fun resumeBooking(bookingId: Long) {
        val booking = getBooking(bookingId) ?: return
        saveBooking(booking.copy(status = ScheduledBookingStatus.ACTIVE))
    }

    suspend fun deleteBooking(bookingId: Long) {
        val current = getAllBookings().toMutableList()
        current.removeAll { it.id == bookingId }
        saveAllBookings(current)
        cancelWork(bookingId)
    }

    suspend fun updateMaxRepeat(bookingId: Long, newMax: Int?) {
        val booking = getBooking(bookingId) ?: return
        saveBooking(booking.copy(maxRepeatCount = newMax))
    }

    suspend fun updateTrainingId(bookingId: Long, newTrainingId: String) {
        val booking = getBooking(bookingId) ?: return
        saveBooking(booking.copy(trainingId = newTrainingId))
    }

    private suspend fun saveAllBookings(bookings: List<ScheduledBooking>) {
        // Simple JSON serialization
        val json = bookings.joinToString(",", "[", "]") { booking ->
            """{"id":"${booking.id}","trainingId":"${booking.trainingId}","className":"${booking.className}","startTime":${booking.startTime},"instructor":"${booking.instructor}","status":"${booking.status.name}","bookedCount":${booking.bookedCount},"maxRepeatCount":${booking.maxRepeatCount ?: "null"},"createdAt":${booking.createdAt}}"""
        }
        context.scheduledBookingDataStore.edit { prefs ->
            prefs[bookingsKey] = json
        }
    }

    private fun parseBookingsFromJson(json: String): List<ScheduledBooking> {
        // Simple JSON parsing - extract objects between { and }
        val result = mutableListOf<ScheduledBooking>()
        val regex = Regex("""\{([^}]+)\}""")
        regex.findAll(json).forEach { match ->
            try {
                val obj = match.value
                val id = obj.findValue("id")?.toLongOrNull() ?: 0L
                val trainingId = obj.findValue("trainingId")
                val className = obj.findValue("className")
                val startTime = obj.findValue("startTime")?.toLongOrNull() ?: 0L
                val instructor = obj.findValue("instructor") ?: ""
                val statusStr = obj.findValue("status")
                val bookedCount = obj.findValue("bookedCount")?.toIntOrNull() ?: 0
                val maxRepeatStr = obj.findValue("maxRepeatCount")
                val maxRepeatCount = if (maxRepeatStr == "null") null else maxRepeatStr?.toIntOrNull()
                val createdAt = obj.findValue("createdAt")?.toLongOrNull() ?: System.currentTimeMillis()

                val status = try {
                    ScheduledBookingStatus.valueOf(statusStr ?: "ACTIVE")
                } catch (e: Exception) {
                    ScheduledBookingStatus.ACTIVE
                }

                //if (id != null && trainingId != null) {
                    result.add(
                        ScheduledBooking(
                            id = id,
                            trainingId = trainingId!!,
                            className = className ?: "",
                            startTime = startTime,
                            instructor = instructor,
                            status = status,
                            bookedCount = bookedCount,
                            maxRepeatCount = maxRepeatCount,
                            createdAt = createdAt
                        )
                    )
                //}
            } catch (e: Exception) {
                // Skip malformed entries
            }
        }
        return result
    }

    private fun String.findValue(key: String): String? {
        val regex = Regex(""""$key":"?([^",}]+)""")
        return regex.find(this)?.groupValues?.get(1)?.removeSurroundingQuotes()
    }

    private fun String.removeSurroundingQuotes(): String {
        return if (startsWith("\"") && endsWith("\"")) {
            substring(1, length - 1)
        } else this
    }

    private fun cancelWork(bookingId: Long) {
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("recurring_booking_$bookingId")
    }
}
