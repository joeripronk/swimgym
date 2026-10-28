package com.swimgym.app.data.repository

import android.content.Context
import com.swimgym.app.data.api.VirtuagymApiClient
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.util.AlarmScheduler
import com.swimgym.app.util.BookingNotificationManager
import com.swimgym.app.util.DstTimeFixtures
import com.swimgym.app.util.wallClockDeltaSeconds
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * [BookingSchedulerImpl.nextOccurrence] advances a weekly class to the first occurrence
 * at or after "now". The advance has to be measured on the local wall clock: adding
 * calendar days keeps the local time of day, while advancing on epoch seconds drifts the
 * weekly class by an hour around a daylight saving transition.
 */
class NextOccurrenceDstTest {

    private lateinit var originalTz: TimeZone
    private lateinit var scheduler: BookingSchedulerImpl

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(DstTimeFixtures.AMSTERDAM))
        scheduler = BookingSchedulerImpl(
            dao = mockk(relaxUnitFun = true),
            scheduledBookingRepo = mockk(relaxUnitFun = true),
            notificationManager = mockk(relaxUnitFun = true),
            alarmScheduler = mockk(relaxUnitFun = true),
            apiClient = mockk(relaxUnitFun = true),
            context = mockk(relaxUnitFun = true),
            calendarService = mockk(relaxUnitFun = true),
            sessionRepository = mockk(relaxUnitFun = true)
        )
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTz)
    }

    @Test
    fun `future base is returned unchanged`() {
        val now = DstTimeFixtures.epochAt("2026-10-20 12:00")
        val base = DstTimeFixtures.epochAt("2026-10-27 19:00")
        assertEquals(base, scheduler.nextOccurrence(base, now))
    }

    @Test
    fun `base equal to now is returned unchanged`() {
        val now = DstTimeFixtures.epochAt("2026-10-27 19:00")
        assertEquals(now, scheduler.nextOccurrence(now, now))
    }

    @Test
    fun `past base advances to the next weekly occurrence across the autumn transition`() {
        val now = DstTimeFixtures.epochAt("2026-10-26 12:00")
        val occurrence = scheduler.nextOccurrence(DstTimeFixtures.epochAt("2026-10-13 19:00"), now)
        assertEquals("2026-10-27 19:00", DstTimeFixtures.local(occurrence))
    }

    @Test
    fun `past base advances to the next weekly occurrence across the spring transition`() {
        val now = DstTimeFixtures.epochAt("2026-04-01 12:00")
        val occurrence = scheduler.nextOccurrence(DstTimeFixtures.epochAt("2026-03-22 19:00"), now)
        assertEquals("2026-04-05 19:00", DstTimeFixtures.local(occurrence))
    }

    @Test
    fun `occurrence keeps the local time of day across every transition week`() {
        val bases = listOf(
            "2026-03-15 19:00",
            "2026-03-22 19:00",
            "2026-03-29 19:00",
            "2026-10-18 19:00",
            "2026-10-25 19:00"
        )
        bases.forEach { base ->
            val now = DstTimeFixtures.epochAt(base) + 3600
            val occurrence = scheduler.nextOccurrence(DstTimeFixtures.epochAt(base), now)
            assertEquals("occurrence of class $base", "19:00", DstTimeFixtures.localTimeOfDay(occurrence))
        }
    }

    @Test
    fun `occurrence is always at least now in wall-clock time`() {
        val now = DstTimeFixtures.epochAt("2026-10-25 02:30")
        val occurrence = scheduler.nextOccurrence(DstTimeFixtures.epochAt("2026-10-11 19:00"), now)
        assertTrue(
            "occurrence must not be in the past",
            wallClockDeltaSeconds(now, occurrence) >= 0
        )
    }

    @Test
    fun `advance is always a whole number of local weeks`() {
        val now = DstTimeFixtures.epochAt("2026-11-02 12:00")
        val base = DstTimeFixtures.epochAt("2026-10-12 19:00")
        val occurrence = scheduler.nextOccurrence(base, now)
        val delta = wallClockDeltaSeconds(base, occurrence)
        assertEquals("advance must be a multiple of 7 local days", 0L, delta % (7 * 86400))
    }
}
