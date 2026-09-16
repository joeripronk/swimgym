package com.swimgym.app.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.swimgym.app.domain.model.Training
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.TrainingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.swimgym.app.data.model.Mappers.toEntity

interface CalendarService {
    suspend fun addTrainingToCalendar(training: Training): String?
    suspend fun removeTrainingFromCalendar(training: TrainingEntity)
}

class CalendarServiceImpl(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val dao: SwimGymDao
) : CalendarService {

    override suspend fun addTrainingToCalendar(training: Training): String? = withContext(Dispatchers.IO) {
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_CALENDAR
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext null
            }

            val selectedCalendarId = sessionRepository.selectedCalendarId.first() ?: 1L
            if (selectedCalendarId < 1) {
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

            // Store eventId and calendarId in the training entity
            val trainingEntity = training.toEntity()
            val updatedTraining = trainingEntity.copy(
                eventId = eventId,
                calendarId = selectedCalendarId.toString()
            )
            dao.insertTraining(updatedTraining)

            if (reminderEnabled && reminderTimes.isNotEmpty()) {
                for (minutes in reminderTimes) {
                    if (minutes > 0) {
                        val reminderValues = android.content.ContentValues().apply {
                            put(CalendarContract.Reminders.EVENT_ID, eventId.toLong())
                            put(CalendarContract.Reminders.MINUTES, minutes)
                            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                        }
                        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                    }
                }
            }

            return@withContext eventId
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    override suspend fun removeTrainingFromCalendar(training: TrainingEntity) = withContext(Dispatchers.IO) {
        try {
            val eventId = training.eventId
            if (eventId.isBlank()) {
                return@withContext
            }

            val calendarId = training.calendarId.toLongOrNull() ?: return@withContext

            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "_id=? AND calendar_id=?",
                arrayOf(eventId, calendarId.toString())
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
