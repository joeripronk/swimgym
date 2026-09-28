package com.swimgym.app.data.repository

import android.util.Log
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import androidx.core.content.ContextCompat
import com.swimgym.app.domain.model.Training
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.TrainingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.swimgym.app.data.model.Mappers.toEntity

data class CalendarEventInfo(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long
)

interface CalendarService {
    suspend fun addTrainingToCalendar(training: Training): String?
    suspend fun addTrainingToCalendar(training: TrainingEntity): String?
    suspend fun removeTrainingFromCalendar(training: TrainingEntity)
    suspend fun findExistingCalendarEntry(startTime: Long, title: String): TrainingEntity?
    suspend fun removeTrainingByTimeAndTitle(startTime: Long, title: String)
    suspend fun isTrainingInCalendar(trainingId: String, startTime: Long, title: String): Boolean
    suspend fun findConflictingEvents(
        startMillis: Long,
        endMillis: Long,
        excludeEventId: String? = null,
        excludeTitle: String? = null
    ): List<CalendarEventInfo>
}

class CalendarServiceImpl(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val dao: SwimGymDao
) : CalendarService {
    companion object {
        private const val TAG = "CalendarService"
    }

    override suspend fun addTrainingToCalendar(training: Training): String? = withContext(Dispatchers.IO) {
        addTrainingToCalendar(training.toEntity())
    }

    override suspend fun addTrainingToCalendar(training: TrainingEntity): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Adding training to calendar: ${training.title} at ${training.startTime}")
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_CALENDAR
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "No WRITE_CALENDAR permission")
                return@withContext null
            }

            val selectedCalendarId = sessionRepository.selectedCalendarId.first() ?: 1L
            if (selectedCalendarId < 1) {
                Log.d(TAG, "No calendar selected")
                return@withContext null
            }

            val reminderEnabled = sessionRepository.getReminderEnabled()
            val reminderTimes = sessionRepository.getReminderTimes()

            val values = android.content.ContentValues().apply {
                put(CalendarContract.Events.DTSTART, training.startTime * 1000)
                put(CalendarContract.Events.DTEND, training.endTime * 1000)
                put(CalendarContract.Events.TITLE, training.title)
                put(
                    CalendarContract.Events.DESCRIPTION,
                    "${training.instructor}\n${training.location}"
                )
                put(
                    CalendarContract.Events.EVENT_LOCATION,
                    "Swimgym, Wibautstraat 131b, 1091 GL Amsterdam"
                )
                put(CalendarContract.Events.CALENDAR_ID, selectedCalendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                if (reminderEnabled && reminderTimes.isNotEmpty()) {
                    put(CalendarContract.Events.HAS_ALARM, true)
                }
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.getLastPathSegment() ?: return@withContext null
            Log.d(TAG, "Event created with ID: $eventId")

            val updatedTraining = training.copy(
                eventId = eventId,
                calendarId = selectedCalendarId.toString()
            )
            dao.insertTraining(updatedTraining)
            Log.d(TAG, "Training entity saved with eventId: $eventId")

            if (reminderEnabled && reminderTimes.isNotEmpty()) {
                for (minutes in reminderTimes) {
                    if (minutes > 0) {
                        val reminderValues = android.content.ContentValues().apply {
                            put(CalendarContract.Reminders.EVENT_ID, eventId.toLong())
                            put(CalendarContract.Reminders.MINUTES, minutes)
                            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                        }
                        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                        Log.d(TAG, "Reminder added: $minutes minutes before")
                    }
                }
            }

            return@withContext eventId
        } catch (e: Exception) {
            Log.e(TAG, "Error adding training to calendar", e)
            return@withContext null
        }
    }

    override suspend fun removeTrainingFromCalendar(training: TrainingEntity) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Removing training from calendar: ${training.id}")
        try {
            val eventId = training.eventId
            if (eventId.isBlank()) {
                Log.d(TAG, "No eventId found, skipping removal")
                return@withContext
            }

            val calendarId = training.calendarId.toLongOrNull() ?: return@withContext

            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "_id=? AND calendar_id=?",
                arrayOf(eventId, calendarId.toString())
            )
            Log.d(TAG, "Calendar event removed: $eventId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing training from calendar", e)
        }
    }

    override suspend fun findExistingCalendarEntry(startTime: Long, title: String): TrainingEntity? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Searching for existing calendar entry: title=$title, startTime=$startTime")
        try {
            val trainings = dao.getAllTrainingsList() ?: return@withContext null
            val found = trainings.find { t ->
                t.startTime == startTime && t.title.equals(title, ignoreCase = true) && t.eventId.isNotBlank()
            }
            Log.d(TAG, "Found existing entry: ${found != null}")
            found
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for existing calendar entry", e)
            return@withContext null
        }
    }

    override suspend fun removeTrainingByTimeAndTitle(startTime: Long, title: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Removing training by time and title: startTime=$startTime, title=$title")
        try {
            val selectedCalendarId = sessionRepository.selectedCalendarId.first()
            if (selectedCalendarId == null || selectedCalendarId < 1) {
                Log.d(TAG, "No calendar selected")
                return@withContext
            }

            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.CALENDAR_ID
                ),
                "${CalendarContract.Events.TITLE} = ? AND ${CalendarContract.Events.DTSTART} = ? AND ${CalendarContract.Events.CALENDAR_ID} = ?",
                arrayOf(title, (startTime * 1000).toString(), selectedCalendarId.toString()),
                null
            )

            var removedCount = 0
            val eventIdIdx = cursor?.getColumnIndex(CalendarContract.Events._ID) ?: -1
            val calendarIdIdx = cursor?.getColumnIndex(CalendarContract.Events.CALENDAR_ID) ?: -1
            if (eventIdIdx < 0 || calendarIdIdx < 0) {
                Log.w(TAG, "Missing required calendar columns (_ID or CALENDAR_ID)")
            } else {
                while (cursor?.moveToNext() == true) {
                    val eventId = cursor.getString(eventIdIdx)
                    val calendarId = cursor.getString(calendarIdIdx)
                    Log.d(TAG, "Found calendar event to remove: id=$eventId, calendar=$calendarId")

                    context.contentResolver.delete(
                        CalendarContract.Events.CONTENT_URI,
                        "${CalendarContract.Events._ID}=? AND ${CalendarContract.Events.CALENDAR_ID}=?",
                        arrayOf(eventId, calendarId)
                    )
                    removedCount++
                }
            }
            Log.d(TAG, "Removed $removedCount calendar events")

            // Update local database entries to remove eventId
            val trainings = dao.getAllTrainingsList() ?: emptyList()
            val matchingTrainings = trainings.filter { t ->
                t.startTime == startTime && t.title.equals(title, ignoreCase = true) && t.eventId.isNotBlank()
            }
            for (training in matchingTrainings) {
                dao.deleteTrainingById(training.id)
                Log.d(TAG, "Removed training from DB: ${training.id}")
            }
            cursor?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing training by time and title", e)
        }
    }

    override suspend fun isTrainingInCalendar(trainingId: String, startTime: Long, title: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Checking if training is in calendar by query: title=$title, startTime=$startTime")
        try {
            val selectedCalendarId = sessionRepository.selectedCalendarId.first()
            if (selectedCalendarId == null || selectedCalendarId < 1) {
                Log.d(TAG, "No calendar selected")
                return@withContext false
            }

            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART
                ),
                "${CalendarContract.Events.TITLE} = ? AND ${CalendarContract.Events.DTSTART} = ? AND ${CalendarContract.Events.CALENDAR_ID} = ?",
                arrayOf(title, (startTime * 1000).toString(), selectedCalendarId.toString()),
                null
            )
            val found = cursor != null && cursor.moveToFirst()
            Log.d(TAG, "Found training in calendar: $found")
            cursor?.close()
            found
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if training is in calendar", e)
            false
        }
    }

    override suspend fun findConflictingEvents(
        startMillis: Long,
        endMillis: Long,
        excludeEventId: String?,
        excludeTitle: String?
    ): List<CalendarEventInfo> = withContext(Dispatchers.IO) {
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_CALENDAR
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "No READ_CALENDAR permission, skipping conflict check")
                return@withContext emptyList()
            }

            val visibleCalendarIds = getVisibleCalendarIds()
            if (visibleCalendarIds.isEmpty()) {
                Log.d(TAG, "No visible calendars found")
                return@withContext emptyList()
            }

            val idPlaceholders = visibleCalendarIds.joinToString(",") { "?" }
            // Timed events store DTSTART/DTEND as UTC millis; all-day events store
            // them as "YYYYMMDD" date strings. The two formats compare differently
            // (lexicographically), so each branch only ever matches its own format.
            val dateStart = toDateOnlyString(startMillis)
            val calEnd = java.util.Calendar.getInstance()
            calEnd.timeInMillis = endMillis
            calEnd.add(java.util.Calendar.DAY_OF_MONTH, 1)
            val dateEndExclusive = toDateOnlyString(calEnd.timeInMillis)
            val selection = "(${CalendarContract.Events.DTSTART} < ? AND ${CalendarContract.Events.DTEND} > ? OR " +
                "${CalendarContract.Events.DTSTART} < ? AND ${CalendarContract.Events.DTEND} > ?) AND " +
                "${CalendarContract.Events.CALENDAR_ID} IN ($idPlaceholders)"
            val selectionArgs = (listOf(endMillis, startMillis, dateEndExclusive, dateStart) + visibleCalendarIds)
                .map { it.toString() }
                .toTypedArray()
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND
                ),
                selection,
                selectionArgs,
                null
            )

            val events = mutableListOf<CalendarEventInfo>()
            cursor?.use { c ->
                val idIdx = c.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = c.getColumnIndex(CalendarContract.Events.DTEND)
                if (idIdx < 0 || titleIdx < 0 || startIdx < 0 || endIdx < 0) {
                    Log.w(TAG, "Missing required event columns (_ID, TITLE, DTSTART or DTEND)")
                    return@use
                }
                while (c.moveToNext()) {
                    events.add(
                        CalendarEventInfo(
                            id = c.getLong(idIdx),
                            title = c.getString(titleIdx) ?: "",
                            startMillis = parseCalendarTime(c.getString(startIdx) ?: ""),
                            endMillis = parseCalendarTime(c.getString(endIdx) ?: "")
                        )
                    )
                }
            }

            val conflicts = events.filter { event ->
                val excludedById = excludeEventId != null && event.id.toString() == excludeEventId
                val excludedByTitle = excludeTitle != null && event.title.equals(excludeTitle, ignoreCase = true)
                !excludedById && !excludedByTitle
            }
            Log.d(TAG, "Found ${conflicts.size} conflicting events in window [$startMillis, $endMillis]")
            conflicts
        } catch (e: Exception) {
            Log.e(TAG, "Error finding conflicting calendar events", e)
            emptyList()
        }
    }

    private fun getVisibleCalendarIds(): List<Long> {
        val ids = mutableListOf<Long>()
        val cursor = context.contentResolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID),
            "${Calendars.VISIBLE} = 1",
            null,
            null
        )
        cursor?.use { c ->
            val idColumn = c.getColumnIndex(Calendars._ID)
            if (idColumn >= 0) {
                while (c.moveToNext()) {
                    ids.add(c.getLong(idColumn))
                }
            }
        }
        return ids
    }

    private fun toDateOnlyString(millis: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        return String.format(
            java.util.Locale.US,
            "%04d%02d%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    private fun parseCalendarTime(value: String): Long {
        // All-day events use "YYYYMMDD"; timed events use UTC millis.
        if (value.length == 8 && value.all { it.isDigit() }) {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            cal.clear()
            cal.set(
                value.substring(0, 4).toInt(),
                value.substring(4, 6).toInt() - 1,
                value.substring(6, 8).toInt()
            )
            return cal.timeInMillis
        }
        return value.toLongOrNull() ?: 0L
    }
}
