package com.swimgym.app.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

fun addDaysToSeconds(seconds: Long, days: Int): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = seconds * 1000
    cal.add(Calendar.DAY_OF_MONTH, days)
    return cal.timeInMillis / 1000
}

/**
 * Difference between two instants measured on the local wall clock.
 *
 * The gym expresses its booking window in local time ("a class can be booked from 7 days
 * before it starts"), while the app stores absolute epoch seconds. Around a daylight saving
 * transition 7 local days are 6d23h or 7d1h of absolute time, so comparing raw epoch
 * seconds shifts the window by an hour. This helper compares the two instants as local
 * wall-clock times instead, which is what the user (and the gym) actually means.
 */
fun wallClockDeltaSeconds(fromSeconds: Long, toSeconds: Long): Long {
    val zone = ZoneId.systemDefault()
    val from = LocalDateTime.ofInstant(Instant.ofEpochSecond(fromSeconds), zone)
    val to = LocalDateTime.ofInstant(Instant.ofEpochSecond(toSeconds), zone)
    return Duration.between(from, to).seconds
}
