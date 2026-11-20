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
        val current = getAllBookings().toMutableList()
        current.removeAll { it.id == booking.id }
        current.add(booking)
        saveAllBookings(current)
    }

    suspend fun advanceToOccurrence(bookingId: Long, occurrence: Long) {
        val booking = getBooking(bookingId) ?: return
        if (booking.startTime == occurrence) return
        saveBooking(booking.copy(
            startTime = occurrence,
            retryCount = 0,
            lastFailureReason = null,
            nextRetryAt = null
        ))
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
	    startTime = addDaysToSeconds(training.startTime, 7),
            instructor = training.instructor,
            trainingId = training.id,
            retryCount = 0,
            lastFailureReason = null,
            nextRetryAt = null
        ))
    }

    suspend fun recordRetryFailure(bookingId: Long, retryCount: Int, reason: RetryFailureReason) {
        val booking = getBooking(bookingId) ?: return
        saveBooking(booking.copy(retryCount = retryCount, lastFailureReason = reason))
    }

    suspend fun setNextRetryAt(bookingId: Long, nextRetryAt: Long) {
        val booking = getBooking(bookingId) ?: return
        saveBooking(booking.copy(nextRetryAt = nextRetryAt))
    }

    suspend fun stopRetries(bookingId: Long) {
        val booking = getBooking(bookingId) ?: return
        saveBooking(booking.copy(
            status = ScheduledBookingStatus.STOPPED,
            nextRetryAt = null
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
        saveBooking(booking.copy(
            status = ScheduledBookingStatus.ACTIVE,
            retryCount = 0,
            lastFailureReason = null,
            nextRetryAt = null
        ))
    }

    suspend fun deleteBooking(bookingId: Long) {
        val current = getAllBookings().toMutableList()
        current.removeAll { it.id == bookingId }
        saveAllBookings(current)
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
        val json = bookings.joinToString(",", "[", "]") { serializeBooking(it) }
        context.scheduledBookingDataStore.edit { prefs ->
            prefs[bookingsKey] = json
        }
    }

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
        // Simple JSON parsing - extract objects between { and }
        val result = mutableListOf<ScheduledBooking>()
        val regex = Regex("""\{([^}]+)\}""")
        regex.findAll(json).forEach { match ->
            try {
                val obj = match.value
                val id = obj.findValue("id")?.toLongOrNull() ?: 0L
                val trainingId = obj.findValue("trainingId")
                val className = obj.findValue("className")
                val startTimeStr = obj.findValue("startTime")
                if (startTimeStr == null) {
                    android.util.Log.w("ScheduledBookingRepo", "dropping corrupt booking entry (no startTime): $obj")
                    return@forEach
                }
                val startTime = startTimeStr.toLongOrNull()
                if (startTime == null) {
                    android.util.Log.w("ScheduledBookingRepo", "dropping corrupt booking entry (bad startTime): $obj")
                    return@forEach
                }
                val instructor = obj.findValue("instructor") ?: ""
                val statusStr = obj.findValue("status")
                val bookedCount = obj.findValue("bookedCount")?.toIntOrNull() ?: 0
                val maxRepeatStr = obj.findValue("maxRepeatCount")
                val maxRepeatCount = if (maxRepeatStr == "null") null else maxRepeatStr?.toIntOrNull()
                val retryCount = obj.findValue("retryCount")?.toIntOrNull() ?: 0
                val lastFailureReason = RetryFailureReason.fromName(obj.findValue("lastFailureReason"))
                val nextRetryAt = obj.findValue("nextRetryAt")?.toLongOrNull()
                val createdAt = obj.findValue("createdAt")?.toLongOrNull() ?: System.currentTimeMillis()

                val status = try {
                    ScheduledBookingStatus.valueOf(statusStr ?: "ACTIVE")
                } catch (e: Exception) {
                    ScheduledBookingStatus.ACTIVE
                }

                if (id == 0L) return@forEach
                if (trainingId == null || className == null) return@forEach
                result.add(
                    ScheduledBooking(
                        id = id,
                        trainingId = trainingId,
                        className = className,
                        startTime = startTime,
                        instructor = instructor,
                        status = status,
                        bookedCount = bookedCount,
                        maxRepeatCount = maxRepeatCount,
                        retryCount = retryCount,
                        lastFailureReason = lastFailureReason,
                        nextRetryAt = nextRetryAt,
                        createdAt = createdAt
                    )
                )
            } catch (e: Exception) {
                // Skip malformed entries
            }
        }
        return result
    }

    private fun String.findValue(key: String): String? {
        val quotedRegex = Regex("\"$key\":\"(.*?)\"(?=[,}]|$)")
        val unquotedRegex = Regex("\"$key\":\\s*([^,}]+)")
        return quotedRegex.find(this)?.groupValues?.get(1)?.unescapeJson()
            ?: unquotedRegex.find(this)?.groupValues?.get(1)?.trim()?.unescapeJson()
    }

    private fun String.removeSurroundingQuotes(): String {
        return if (startsWith("\"") && endsWith("\"")) {
            substring(1, length - 1)
        } else this
    }

    private fun String.unescapeJson(): String {
        return replace("\\\"", "\"").replace("\\\\", "\\")
    }
}
