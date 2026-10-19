package com.swimgym.app.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TimeUtilsTest {

    private lateinit var originalTz: TimeZone

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTz)
    }

    private fun wallClock(seconds: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return format.format(Date(seconds * 1000))
    }

    private fun epoch(date: String): Long {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return format.parse(date)!!.time / 1000
    }

    @Test
    fun `addDays keeps wall clock across spring forward`() {
        assertEquals("2026-04-05 18:00", wallClock(addDaysToSeconds(epoch("2026-03-29 18:00"), 7)))
    }

    @Test
    fun `addDays keeps wall clock across autumn transition`() {
        assertEquals("2026-11-01 18:00", wallClock(addDaysToSeconds(epoch("2026-10-25 18:00"), 7)))
    }

    @Test
    fun `addDays subtracts week keeping wall clock`() {
        assertEquals("2026-03-29 18:00", wallClock(addDaysToSeconds(epoch("2026-04-05 18:00"), -7)))
    }
}
