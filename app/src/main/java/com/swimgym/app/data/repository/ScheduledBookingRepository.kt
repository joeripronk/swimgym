package com.swimgym.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.swimgym.app.data.local.entity.TrainingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.swimgym.app.util.addDaysToSeconds

private val Context.scheduledBookingDataStore by preferencesDataStore(name = "scheduled_bookings")

class ScheduledBookingRepository(
    private val context: Context
) {
    private val bookingsKey = stringPreferencesKey("scheduled_bookings_list")

    suspend fun saveBooking(booking: ScheduledBooking) {
        context.scheduledBookingDataStore.edit { prefs ->
            val current = parseBookingsFromJson(prefs[bookingsKey] ?: "[]").toMutableList()
            current.removeAll { it.id == booking.id }
            current.add(booking)
            prefs[bookingsKey] = serializeAll(current)
        }
    }

    private suspend fun updateBooking(
        bookingId: Long,
        transform: (ScheduledBooking) -> ScheduledBooking
    ) {
        context.scheduledBookingDataStore.edit { prefs ->
            val current = parseBookingsFromJson(prefs[bookingsKey] ?: "[]").toMutableList()
            val index = current.indexOfFirst { it.id == bookingId }
            if (index >= 0) current[index] = transform(current[index])
            prefs[bookingsKey] = serializeAll(current)
        }
    }

    suspend fun advanceToOccurrence(bookingId: Long, occurrence: Long) {
        updateBooking(bookingId) { booking ->
            if (booking.startTime == occurrence) booking else booking.copy(
                startTime = occurrence,
                retryCount = 0,
                lastFailureReason = null,
                nextRetryAt = null
            )
        }
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
        updateBooking(bookingId) { booking ->
            booking.copy(
                bookedCount = booking.bookedCount + 1,
                startTime = addDaysToSeconds(training.startTime, 7),
                instructor = training.instructor,
                trainingId = training.id,
                retryCount = 0,
                lastFailureReason = null,
                nextRetryAt = null
            )
        }
    }

    suspend fun recordRetryFailure(bookingId: Long, retryCount: Int, reason: RetryFailureReason) {
        updateBooking(bookingId) { it.copy(retryCount = retryCount, lastFailureReason = reason) }
    }

    suspend fun setNextRetryAt(bookingId: Long, nextRetryAt: Long) {
        updateBooking(bookingId) { it.copy(nextRetryAt = nextRetryAt) }
    }

    suspend fun stopRetries(bookingId: Long) {
        updateBooking(bookingId) {
            it.copy(status = ScheduledBookingStatus.STOPPED, nextRetryAt = null)
        }
    }

    suspend fun markBookingAsInvalid(bookingId: Long) {
        updateBooking(bookingId) { it.copy(status = ScheduledBookingStatus.INVALIDATED) }
    }

    suspend fun completeBooking(bookingId: Long) {
        updateBooking(bookingId) { it.copy(status = ScheduledBookingStatus.COMPLETED) }
    }

    suspend fun pauseBooking(bookingId: Long) {
        updateBooking(bookingId) { it.copy(status = ScheduledBookingStatus.PAUSED) }
    }

    suspend fun resumeBooking(bookingId: Long) {
        updateBooking(bookingId) {
            it.copy(
                status = ScheduledBookingStatus.ACTIVE,
                retryCount = 0,
                lastFailureReason = null,
                nextRetryAt = null
            )
        }
    }

    suspend fun deleteBooking(bookingId: Long) {
        context.scheduledBookingDataStore.edit { prefs ->
            val current = parseBookingsFromJson(prefs[bookingsKey] ?: "[]")
                .filterNot { it.id == bookingId }
            prefs[bookingsKey] = serializeAll(current)
        }
    }

    suspend fun updateMaxRepeat(bookingId: Long, newMax: Int?) {
        updateBooking(bookingId) { it.copy(maxRepeatCount = newMax) }
    }

    suspend fun updateTrainingId(bookingId: Long, newTrainingId: String) {
        updateBooking(bookingId) { it.copy(trainingId = newTrainingId) }
    }

    private fun serializeAll(bookings: List<ScheduledBooking>): String =
        bookings.joinToString(",", "[", "]") { serializeBooking(it) }

    private fun serializeBooking(booking: ScheduledBooking): String = buildString {
        append("{\"id\":\"").append(escapeJson(booking.id.toString())).append("\"")
        append(",\"trainingId\":\"").append(escapeJson(booking.trainingId)).append("\"")
        append(",\"className\":\"").append(escapeJson(booking.className)).append("\"")
        append(",\"startTime\":").append(booking.startTime)
        append(",\"instructor\":\"").append(escapeJson(booking.instructor)).append("\"")
        append(",\"status\":\"").append(escapeJson(booking.status.name)).append("\"")
        append(",\"bookedCount\":").append(booking.bookedCount)
        append(",\"maxRepeatCount\":")
        append(booking.maxRepeatCount?.let { "\"$it\"" } ?: "null")
        append(",\"retryCount\":").append(booking.retryCount)
        append(",\"lastFailureReason\":")
        append(booking.lastFailureReason?.let { "\"${escapeJson(it.name)}\"" } ?: "null")
        append(",\"nextRetryAt\":").append(booking.nextRetryAt ?: "null")
        append(",\"createdAt\":").append(booking.createdAt)
        append("}")
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun parseBookingsFromJson(json: String): List<ScheduledBooking> {
        val result = mutableListOf<ScheduledBooking>()
        try {
            val array = com.google.gson.JsonParser.parseString(json).asJsonArray
            for (element in array) {
                if (!element.isJsonObject) continue
                try {
                    val obj = element.asJsonObject
                    val id = obj.longOrNull("id") ?: 0L
                    val trainingId = obj.stringOrNull("trainingId")
                    val className = obj.stringOrNull("className")
                    val startTime = obj.longOrNull("startTime")
                    if (id == 0L || trainingId == null || className == null || startTime == null) {
                        android.util.Log.w("ScheduledBookingRepo", "dropping corrupt booking entry: $element")
                        continue
                    }
                    val instructor = obj.stringOrNull("instructor") ?: ""
                    val status = runCatching {
                        ScheduledBookingStatus.valueOf(obj.stringOrNull("status") ?: "ACTIVE")
                    }.getOrDefault(ScheduledBookingStatus.ACTIVE)
                    result.add(
                        ScheduledBooking(
                            id = id,
                            trainingId = trainingId,
                            className = className,
                            startTime = startTime,
                            instructor = instructor,
                            status = status,
                            bookedCount = obj.intOrNull("bookedCount") ?: 0,
                            maxRepeatCount = obj.intOrNull("maxRepeatCount"),
                            retryCount = obj.intOrNull("retryCount") ?: 0,
                            lastFailureReason = RetryFailureReason.fromName(obj.stringOrNull("lastFailureReason")),
                            nextRetryAt = obj.longOrNull("nextRetryAt"),
                            createdAt = obj.longOrNull("createdAt") ?: System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    // Skip a single malformed entry without losing the rest.
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ScheduledBookingRepo", "Failed to parse bookings JSON: ${e.message}")
        }
        return result
    }

    private fun com.google.gson.JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun com.google.gson.JsonObject.longOrNull(key: String): Long? =
        stringOrNull(key)?.toLongOrNull()

    private fun com.google.gson.JsonObject.intOrNull(key: String): Int? =
        stringOrNull(key)?.toIntOrNull()
}
