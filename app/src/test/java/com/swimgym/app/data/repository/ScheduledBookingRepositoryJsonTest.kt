package com.swimgym.app.data.repository

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScheduledBookingRepositoryJsonTest {

    private val testBooking = ScheduledBooking(
        id = 12345L,
        trainingId = "abc123",
        className = "Laps Swimming",
        startTime = 1700000000L,
        instructor = "John",
        status = ScheduledBookingStatus.ACTIVE,
        bookedCount = 2,
        maxRepeatCount = 5
    )

    @Test
    fun `serialize and deserialize booking`() {
        val json = listOf(testBooking).joinToString(",", "[", "]") { booking ->
            """{"id":"${booking.id}","trainingId":"${booking.trainingId}","className":"${booking.className}","startTime":${booking.startTime},"instructor":"${booking.instructor}","status":"${booking.status.name}","bookedCount":${booking.bookedCount},"maxRepeatCount":${booking.maxRepeatCount?.let { "\"$it\"" } ?: "null"},"createdAt":${booking.createdAt}}"""
        }

        val parsed = parseBookingsFromJson(json)
        assertEquals(1, parsed.size)
        assertEquals(testBooking.id, parsed[0].id)
        assertEquals(testBooking.className, parsed[0].className)
        assertEquals(testBooking.startTime, parsed[0].startTime)
        assertEquals(testBooking.instructor, parsed[0].instructor)
        assertEquals(testBooking.status, parsed[0].status)
        assertEquals(testBooking.bookedCount, parsed[0].bookedCount)
        assertEquals(testBooking.maxRepeatCount, parsed[0].maxRepeatCount)
    }

    @Test
    fun `serialize and deserialize multiple bookings`() {
        val bookings = listOf(
            testBooking,
            testBooking.copy(id = 99L, className = "Water Polo")
        )
        val json = bookings.joinToString(",", "[", "]") { booking ->
            """{"id":"${booking.id}","trainingId":"${booking.trainingId}","className":"${booking.className}","startTime":${booking.startTime},"instructor":"${booking.instructor}","status":"${booking.status.name}","bookedCount":${booking.bookedCount},"maxRepeatCount":${booking.maxRepeatCount?.let { "\"$it\"" } ?: "null"},"createdAt":${booking.createdAt}}"""
        }

        val parsed = parseBookingsFromJson(json)
        assertEquals(2, parsed.size)
    }

    @Test
    fun `empty json returns empty list`() {
        val parsed = parseBookingsFromJson("[]")
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `null json returns empty list`() {
        val parsed = parseBookingsFromJson("")
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `booking with null maxRepeatCount`() {
        val booking = testBooking.copy(maxRepeatCount = null)
        val json = listOf(booking).joinToString(",", "[", "]") { b ->
            """{"id":"${b.id}","trainingId":"${b.trainingId}","className":"${b.className}","startTime":${b.startTime},"instructor":"${b.instructor}","status":"${b.status.name}","bookedCount":${b.bookedCount},"maxRepeatCount":${b.maxRepeatCount?.let { "\"$it\"" } ?: "null"},"createdAt":${b.createdAt}}"""
        }

        val parsed = parseBookingsFromJson(json)
        assertEquals(null, parsed[0].maxRepeatCount)
    }

    @Test
    fun `malformed entry is skipped`() {
        val json = """[{"id":"0","trainingId":"","className":""},{"id":12345,"trainingId":"abc","className":"Test","startTime":1700000000,"instructor":"X","status":"ACTIVE","bookedCount":0,"maxRepeatCount":null,"createdAt":1700000000}]"""
        val parsed = parseBookingsFromJson(json)
        assertEquals(1, parsed.size)
        assertEquals(12345L, parsed[0].id)
    }

    private fun parseBookingsFromJson(json: String): List<ScheduledBooking> {
        val result = mutableListOf<ScheduledBooking>()
        val regex = Regex("""\{([^}]+)\}""")
        regex.findAll(json).forEach { match ->
            try {
                val obj = match.value
                val id = obj.findValue("id")?.toLongOrNull() ?: 0L
                val trainingId = obj.findValue("trainingId")
                val className = obj.findValue("className")
                val startTime = obj.findValue("startTime")!!.toLong()
                val instructor = obj.findValue("instructor") ?: ""
                val statusStr = obj.findValue("status")
                val bookedCount = obj.findValue("bookedCount")?.toIntOrNull() ?: 0
                val maxRepeatStr = obj.findValue("maxRepeatCount")
                val maxRepeatCount = if (maxRepeatStr == "null") null else maxRepeatStr?.toIntOrNull()
                val createdAt = obj.findValue("createdAt")?.toLongOrNull() ?: System.currentTimeMillis()

                val status = try {
                    ScheduledBookingStatus.valueOf(statusStr ?: "ACTIVE")
                } catch (e: Exception) {
                    ScheduledBookingStatus.ACTIVE
                }

                if (id == 0L) return@forEach
                if (trainingId == null || className == null) return@forEach
                result.add(
                    ScheduledBooking(
                        id = id,
                        trainingId = trainingId,
                        className = className,
                        startTime = startTime,
                        instructor = instructor,
                        status = status,
                        bookedCount = bookedCount,
                        maxRepeatCount = maxRepeatCount,
                        createdAt = createdAt
                    )
                )
            } catch (e: Exception) {
                // Skip malformed entries
            }
        }
        return result
    }

    private fun String.findValue(key: String): String? {
        val quotedRegex = Regex("\"$key\":\"(.*?)\"(?=[,}]|$)")
        val unquotedRegex = Regex("\"$key\":\\s*([^,}]+)")
        return quotedRegex.find(this)?.groupValues?.get(1)?.unescapeJson()
            ?: unquotedRegex.find(this)?.groupValues?.get(1)?.trim()?.unescapeJson()
    }

    private fun String.unescapeJson(): String {
        return replace("\\\"", "\"").replace("\\\\", "\\")
    }
}
