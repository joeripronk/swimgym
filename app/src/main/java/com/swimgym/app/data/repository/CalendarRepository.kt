package com.swimgym.app.data.repository

import android.content.Context
import android.provider.CalendarContract
import com.swimgym.app.domain.model.CalendarInfo
import com.swimgym.app.domain.repository.CalendarRepository

class CalendarRepositoryImpl(
    private val context: Context
) : CalendarRepository {

    override suspend fun getAvailableCalendars(): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        val contentResolver = context.contentResolver

        try {
            val allCalendars = contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.OWNER_ACCOUNT,
                    CalendarContract.Calendars.IS_PRIMARY
                ),
                null,
                null,
                null
            )

            allCalendars?.use { cursor ->
                val idColumn = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val nameColumn = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountColumn = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val ownerColumn = cursor.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)
                val primaryColumn = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(nameColumn) ?: "Unknown"
                    val accountName = cursor.getString(accountColumn) ?: ""
                    val ownerAccount = cursor.getString(ownerColumn) ?: ""
                    val isPrimary = cursor.getInt(primaryColumn) == 1

                    calendars.add(
                        CalendarInfo(
                            id = id,
                            displayName = displayName,
                            accountName = accountName,
                            ownerAccount = ownerAccount,
                            isPrimary = isPrimary
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            throw e
        }

        return calendars.sortedByDescending { it.isPrimary }
    }
}
