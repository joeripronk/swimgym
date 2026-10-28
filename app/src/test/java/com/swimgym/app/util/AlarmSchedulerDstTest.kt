package com.swimgym.app.util

import android.app.AlarmManager
import android.content.Context
import com.swimgym.app.data.repository.ScheduledBooking
import com.swimgym.app.data.repository.SessionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * The booking alarm for a recurring class has to fire at the *local* time of the class,
 * 7 local days earlier plus 5 minutes (the booking window opens 7 days before the class).
 *
 * Around a daylight saving transition 7 local days are not 7 * 24 absolute hours, so these
 * tests pin down that the alarm keeps the local wall-clock time of the class.
 */
class AlarmSchedulerDstTest {

    private lateinit var originalTz: TimeZone
    private lateinit var context: Context
    private lateinit var sessionRepository: SessionRepository
    private lateinit var alarmScheduler: AlarmScheduler

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(DstTimeFixtures.AMSTERDAM))

        context = mockk<Context>(relaxUnitFun = true)
        val alarmManager = mockk<AlarmManager>(relaxed = true)
        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        sessionRepository = mockk(relaxUnitFun = true)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTz)
    }

    private fun schedulerAt(now: Long): AlarmScheduler =
        AlarmScheduler(context, sessionRepository) { now * 1000 }

    private fun bookingFor(classLocalTime: String, id: Long = 1L): ScheduledBooking =
        ScheduledBooking(
            id = id,
            trainingId = "t1",
            className = "Laps Swimming",
            startTime = DstTimeFixtures.epochAt(classLocalTime)
        )

    @Test
    fun `alarm for a class a week after the autumn transition keeps the local class time`() {
        // Class on 2026-10-27 19:00 local (winter time), alarm on 2026-10-20 19:05 local
        // (still summer time).
        val scheduler = schedulerAt(DstTimeFixtures.epochAt("2026-10-20 12:00"))

        val alarm = scheduler.bookingAlarmTimeMillis(bookingFor("2026-10-27 19:00"))

        assertEquals("2026-10-20 19:05", DstTimeFixtures.local(alarm / 1000))
    }

    @Test
    fun `alarm for a class a week after the spring transition keeps the local class time`() {
        // Class on 2026-04-05 19:00 local (summer time), alarm on 2026-03-29 19:05 local
        // (the transition day itself).
        val scheduler = schedulerAt(DstTimeFixtures.epochAt("2026-03-29 12:00"))

        val alarm = scheduler.bookingAlarmTimeMillis(bookingFor("2026-04-05 19:00"))

        assertEquals("2026-03-29 19:05", DstTimeFixtures.local(alarm / 1000))
    }

    @Test
    fun `alarm for a class on the autumn transition day keeps the local class time`() {
        val scheduler = schedulerAt(DstTimeFixtures.epochAt("2026-10-18 12:00"))

        val alarm = scheduler.bookingAlarmTimeMillis(bookingFor("2026-10-25 19:00"))

        assertEquals("2026-10-18 19:05", DstTimeFixtures.local(alarm / 1000))
    }

    @Test
    fun `alarm for a class on the spring transition day keeps the local class time`() {
        val scheduler = schedulerAt(DstTimeFixtures.epochAt("2026-03-22 12:00"))

        val alarm = scheduler.bookingAlarmTimeMillis(bookingFor("2026-03-29 19:00"))

        assertEquals("2026-03-22 19:05", DstTimeFixtures.local(alarm / 1000))
    }

    @Test
    fun `alarm is seven local days before the class and five minutes after the window opens`() {
        val classTimes = listOf(
            "2026-03-15 19:00",
            "2026-03-22 19:00",
            "2026-03-29 19:00",
            "2026-04-05 19:00",
            "2026-10-18 19:00",
            "2026-10-25 19:00",
            "2026-11-01 19:00"
        )

        classTimes.forEach { classTime ->
            val classEpoch = DstTimeFixtures.epochAt(classTime)
            // pretend we are a week before the class
            val scheduler = schedulerAt(classEpoch - 7 * 86400 - 3600)

            val alarm = scheduler.bookingAlarmTimeMillis(bookingFor(classTime))

            val alarmLocal = DstTimeFixtures.local(alarm / 1000)
            val expectedLocal = DstTimeFixtures.local(DstTimeFixtures.epochAt(classTime) - 7 * 86400)
            assertEquals("alarm for class $classTime", "${expectedLocal.substringBefore(' ')} 19:05", alarmLocal)
            assertEquals("alarm for class $classTime", "19:05", DstTimeFixtures.localTimeOfDay(alarm / 1000))
        }
    }

    @Test
    fun `alarm rolls forward to the next weekly occurrence when the stored class has passed`() {
        // The stored class (2026-10-13 19:00) is in the past, the next occurrence is
        // 2026-10-20 19:00 local, so its window opened at 2026-10-13 19:00 and the
        // alarm (19:05) is in the past: scheduleBooking fires the booking immediately.
        val scheduler = schedulerAt(DstTimeFixtures.epochAt("2026-10-20 12:00"))

        val alarm = scheduler.bookingAlarmTimeMillis(bookingFor("2026-10-13 19:00"))

        assertEquals("2026-10-13 19:05", DstTimeFixtures.local(alarm / 1000))
    }

    @Test
    fun `alarm belongs to the next week when the current occurrence already started`() {
        // 19:30 local on 2026-10-20: today's class (19:00) has started, so the next
        // occurrence is 2026-10-27 19:00 and its window opened at 2026-10-20 19:00;
        // the alarm (19:05) is in the past and fires immediately.
        val scheduler = schedulerAt(DstTimeFixtures.epochAt("2026-10-20 19:30"))

        val alarm = scheduler.bookingAlarmTimeMillis(bookingFor("2026-10-20 19:00"))

        assertEquals("2026-10-20 19:05", DstTimeFixtures.local(alarm / 1000))
    }

    @Test
    fun `alarm for a class two weeks after the autumn transition keeps the local class time`() {
        val scheduler = schedulerAt(DstTimeFixtures.epochAt("2026-11-01 12:00"))

        val alarm = scheduler.bookingAlarmTimeMillis(bookingFor("2026-11-08 19:00"))

        assertEquals("2026-11-01 19:05", DstTimeFixtures.local(alarm / 1000))
    }
}
