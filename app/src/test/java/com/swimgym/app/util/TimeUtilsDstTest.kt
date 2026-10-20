package com.swimgym.app.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * DST behaviour of the wall-clock day arithmetic that the whole booking pipeline is built on.
 *
 * Europe/Amsterdam transitions (2026):
 *  - spring forward: 2026-03-29 02:00 -> 03:00 (23 hour day)
 *  - autumn back:    2026-10-25 03:00 -> 02:00 (25 hour day)
 */
class TimeUtilsDstTest {

    private lateinit var originalTz: TimeZone

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(DstTimeFixtures.AMSTERDAM))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTz)
    }

    @Test
    fun `a week forward across the spring transition keeps the local class time`() {
        val classTime = DstTimeFixtures.epochAt("2026-03-29 19:00")

        val next = addDaysToSeconds(classTime, 7)

        assertEquals("2026-04-05 19:00", DstTimeFixtures.local(next))
    }

    @Test
    fun `a week forward across the autumn transition keeps the local class time`() {
        val classTime = DstTimeFixtures.epochAt("2026-10-25 19:00")

        val next = addDaysToSeconds(classTime, 7)

        assertEquals("2026-11-01 19:00", DstTimeFixtures.local(next))
    }

    @Test
    fun `a week forward across the spring transition is 23 absolute hours`() {
        // 2026-03-22 19:00 is still winter time, 2026-03-29 19:00 is already summer time
        val classTime = DstTimeFixtures.epochAt("2026-03-22 19:00")

        val next = addDaysToSeconds(classTime, 7)

        assertEquals("2026-03-29 19:00", DstTimeFixtures.local(next))
        assertEquals(7 * 86400 - 3600, next - classTime)
    }

    @Test
    fun `a week forward across the autumn transition is 25 absolute hours`() {
        // 2026-10-18 19:00 is still summer time, 2026-10-25 19:00 is already winter time
        val classTime = DstTimeFixtures.epochAt("2026-10-18 19:00")

        val next = addDaysToSeconds(classTime, 7)

        assertEquals("2026-10-25 19:00", DstTimeFixtures.local(next))
        assertEquals(7 * 86400 + 3600, next - classTime)
    }

    @Test
    fun `a week forward without a transition is exactly 7 absolute days`() {
        val classTime = DstTimeFixtures.epochAt("2026-10-13 19:00")

        val next = addDaysToSeconds(classTime, 7)

        assertEquals(7 * 86400, next - classTime)
        assertEquals("2026-10-20 19:00", DstTimeFixtures.local(next))
    }

    @Test
    fun `a week backward across the autumn transition keeps the local class time`() {
        val classTime = DstTimeFixtures.epochAt("2026-11-01 19:00")

        val previous = addDaysToSeconds(classTime, -7)

        assertEquals("2026-10-25 19:00", DstTimeFixtures.local(previous))
    }

    @Test
    fun `a week backward across the spring transition keeps the local class time`() {
        val classTime = DstTimeFixtures.epochAt("2026-04-05 19:00")

        val previous = addDaysToSeconds(classTime, -7)

        assertEquals("2026-03-29 19:00", DstTimeFixtures.local(previous))
    }

    @Test
    fun `seven local days ahead of the alarm moment keeps the local alarm time across the autumn transition`() {
        // 2026-10-20 19:05 local (still summer time), the class is a week later at 19:00
        // local (winter time). 7 local days ahead is 2026-10-27 19:05 local, which is
        // 7 days + 55 minutes of absolute time.
        val alarmMoment = DstTimeFixtures.epochAt("2026-10-20 19:05")

        val windowBound = addDaysToSeconds(alarmMoment, 7)

        assertEquals("2026-10-27 19:05", DstTimeFixtures.local(windowBound))
        assertEquals(7 * 86400 + 3600, windowBound - alarmMoment)
    }

    @Test
    fun `a local time that does not exist on the spring transition day is normalised`() {
        // 02:30 does not exist on 2026-03-29 in Amsterdam: the clock jumps 02:00 -> 03:00.
        // java.util.Calendar resolves the gap with the pre-transition offset, so the class
        // ends up half an hour earlier in local time.
        val gap = DstTimeFixtures.epochAt("2026-03-28 02:30")

        val moved = addDaysToSeconds(gap, 1)

        assertEquals("2026-03-29 01:30", DstTimeFixtures.local(moved))
        assertEquals(86400 - 3600, moved - gap)
    }

    @Test
    fun `four weekly rolls keep the local class time across both transitions`() {
        var classTime = DstTimeFixtures.epochAt("2026-03-02 19:00")

        classTime = addDaysToSeconds(classTime, 7)
        assertEquals("2026-03-09 19:00", DstTimeFixtures.local(classTime))

        classTime = addDaysToSeconds(classTime, 7)
        assertEquals("2026-03-16 19:00", DstTimeFixtures.local(classTime))

        classTime = addDaysToSeconds(classTime, 7)
        assertEquals("2026-03-23 19:00", DstTimeFixtures.local(classTime))

        classTime = addDaysToSeconds(classTime, 7)
        assertEquals("2026-03-30 19:00", DstTimeFixtures.local(classTime))
    }
}
