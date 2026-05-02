package com.swimgym.app.domain.repository

import com.swimgym.app.domain.model.CalendarInfo

interface CalendarRepository {
    suspend fun getAvailableCalendars(): List<CalendarInfo>
}
