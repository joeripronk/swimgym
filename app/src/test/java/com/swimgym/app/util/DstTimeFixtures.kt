package com.swimgym.app.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Helpers for writing tests about wall-clock time and daylight saving transitions.
 *
 * The gym timetable is expressed in local wall-clock time (a class is always at 19:00 local,
 * every week). The app stores absolute epoch seconds, so every "7 days" in the booking
 * pipeline has to be interpreted as 7 *local* days, not 7 * 24 absolute hours.
 */
object DstTimeFixtures {

    const val AMSTERDAM = "Europe/Amsterdam"

    /**
     * Epoch seconds for a local wall-clock time ("yyyy-MM-dd HH:mm").
     *
     * The UTC offset is resolved for that local date, which is exactly what
     * java.util.Calendar (and therefore ScheduleParser) does: a class at 19:00 on
     * 2026-10-27 in Amsterdam is 18:00 UTC because the autumn transition already happened,
     * even when it is parsed on 2026-10-20 while the device is still on summer time.
     */
    fun epochAt(local: String, zoneId: String = AMSTERDAM): Long =
        ZonedDateTime.of(LocalDateTime.parse(normalize(local)), ZoneId.of(zoneId)).toEpochSecond()

    /** Local wall-clock time ("yyyy-MM-dd HH:mm") of an epoch-second value. */
    fun local(epochSeconds: Long, zoneId: String = AMSTERDAM): String =
        ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.of(zoneId))
            .toLocalDateTime()
            .toString()
            .replace('T', ' ')
            .substring(0, 16)

    /** Local time of day ("HH:mm") of an epoch-second value. */
    fun localTimeOfDay(epochSeconds: Long, zoneId: String = AMSTERDAM): String =
        local(epochSeconds, zoneId).substringAfter(' ')

    private fun normalize(local: String): String =
        if (local.contains('T')) local else local.replace(' ', 'T')
}
