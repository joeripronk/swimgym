package com.swimgym.app.data.repository

import android.content.Context
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class ScheduledBookingRepositoryJsonTest {
    private val repository = ScheduledBookingRepository(mock(Context::class.java))

    private val testBooking = ScheduledBooking(
        id = 12345L,
        trainingId = "abc123",
        className = "Laps Swimming",
        startTime = 1700000000L,
        instructor = "John",
        status = ScheduledBookingStatus.ACTIVE,
        bookedCount = 2,
        maxRepeatCount = 5,
        retryCount = 3,
        lastFailureReason = RetryFailureReason.CLASS_FULL,
        nextRetryAt = 1700003600L,
        createdAt = 1600000000L
    )

    @Test
    fun `round trip preserves all fields`() {
        val json = repository.serializeAll(listOf(testBooking))
        val parsed = repository.parseBookingsFromJson(json)

        assertEquals(1, parsed.size)
        val booking = parsed[0]
        assertEquals(testBooking.id, booking.id)
        assertEquals(testBooking.trainingId, booking.trainingId)
        assertEquals(testBooking.className, booking.className)
        assertEquals(testBooking.startTime, booking.startTime)
        assertEquals(testBooking.instructor, booking.instructor)
        assertEquals(testBooking.status, booking.status)
        assertEquals(testBooking.bookedCount, booking.bookedCount)
        assertEquals(testBooking.maxRepeatCount, booking.maxRepeatCount)
        assertEquals(testBooking.retryCount, booking.retryCount)
        assertEquals(testBooking.lastFailureReason, booking.lastFailureReason)
        assertEquals(testBooking.nextRetryAt, booking.nextRetryAt)
        assertEquals(testBooking.createdAt, booking.createdAt)
    }

    @Test
    fun `round trip preserves null values`() {
        val booking = testBooking.copy(
            maxRepeatCount = null,
            lastFailureReason = null,
            nextRetryAt = null
        )
        val json = repository.serializeAll(listOf(booking))
        val parsed = repository.parseBookingsFromJson(json)

        assertTrue(json.contains("\"maxRepeatCount\":null"))
        assertTrue(json.contains("\"lastFailureReason\":null"))
        assertTrue(json.contains("\"nextRetryAt\":null"))
        assertEquals(1, parsed.size)
        assertNull(parsed[0].maxRepeatCount)
        assertNull(parsed[0].lastFailureReason)
        assertNull(parsed[0].nextRetryAt)
    }

    @Test
    fun `serialize and deserialize multiple bookings`() {
        val bookings = listOf(
            testBooking,
            testBooking.copy(
                id = 99L,
                className = "Water Polo",
                status = ScheduledBookingStatus.PAUSED,
                lastFailureReason = null
            )
        )

        val parsed = repository.parseBookingsFromJson(repository.serializeAll(bookings))

        assertEquals(2, parsed.size)
        assertEquals(12345L, parsed[0].id)
        assertEquals("Laps Swimming", parsed[0].className)
        assertEquals(99L, parsed[1].id)
        assertEquals("Water Polo", parsed[1].className)
        assertEquals(ScheduledBookingStatus.PAUSED, parsed[1].status)
        assertNull(parsed[1].lastFailureReason)
    }

    @Test
    fun `empty json returns empty list`() {
        assertTrue(repository.parseBookingsFromJson("[]").isEmpty())
        assertTrue(repository.parseBookingsFromJson(repository.serializeAll(emptyList())).isEmpty())
    }

    @Test
    fun `invalid json returns empty list`() {
        assertTrue(repository.parseBookingsFromJson("").isEmpty())
        assertTrue(repository.parseBookingsFromJson("not json").isEmpty())
        assertTrue(repository.parseBookingsFromJson("{}").isEmpty())
    }

    @Test
    fun `malformed entry is skipped`() {
        val json = """[{"id":"0","trainingId":"","className":""},{"id":12345,"trainingId":"abc","className":"Test","startTime":1700000000,"instructor":"X","status":"ACTIVE","bookedCount":0,"maxRepeatCount":null,"createdAt":1700000000}]"""

        val parsed = repository.parseBookingsFromJson(json)

        assertEquals(1, parsed.size)
        assertEquals(12345L, parsed[0].id)
    }

    @Test
    fun `entry missing required field is skipped`() {
        val json = """[{"id":5,"trainingId":"t","className":"C"},{"id":6,"trainingId":"t2","className":"C2","startTime":10}]"""

        val parsed = repository.parseBookingsFromJson(json)

        assertEquals(1, parsed.size)
        assertEquals(6L, parsed[0].id)
    }

    @Test
    fun `non object entries are skipped`() {
        val json = """["garbage", 42, null, {"id":7,"trainingId":"t","className":"C","startTime":1}]"""

        val parsed = repository.parseBookingsFromJson(json)

        assertEquals(1, parsed.size)
        assertEquals(7L, parsed[0].id)
    }

    @Test
    fun `unknown failure reason is ignored on deserialize`() {
        val json = """[{"id":"1","trainingId":"t","className":"C","startTime":1,"status":"STOPPED","retryCount":4,"lastFailureReason":"SOMETHING_NEW"}]"""

        val parsed = repository.parseBookingsFromJson(json)

        assertEquals(1, parsed.size)
        assertEquals(ScheduledBookingStatus.STOPPED, parsed[0].status)
        assertEquals(4, parsed[0].retryCount)
        assertNull(parsed[0].lastFailureReason)
    }

    @Test
    fun `unknown status falls back to active`() {
        val json = """[{"id":2,"trainingId":"t","className":"C","startTime":1,"status":"BOGUS"}]"""

        val parsed = repository.parseBookingsFromJson(json)

        assertEquals(1, parsed.size)
        assertEquals(ScheduledBookingStatus.ACTIVE, parsed[0].status)
    }

    @Test
    fun `legacy quoted numeric fields are parsed`() {
        val json = """[{"id":"777","trainingId":"t","className":"C","startTime":"1700000000","instructor":"I","status":"ACTIVE","bookedCount":"4","maxRepeatCount":"5","retryCount":"2","lastFailureReason":"CLASS_FULL","nextRetryAt":"1700003600","createdAt":"1600000000"}]"""

        val parsed = repository.parseBookingsFromJson(json)

        assertEquals(1, parsed.size)
        val booking = parsed[0]
        assertEquals(777L, booking.id)
        assertEquals(1700000000L, booking.startTime)
        assertEquals(4, booking.bookedCount)
        assertEquals(5, booking.maxRepeatCount)
        assertEquals(2, booking.retryCount)
        assertEquals(RetryFailureReason.CLASS_FULL, booking.lastFailureReason)
        assertEquals(1700003600L, booking.nextRetryAt)
        assertEquals(1600000000L, booking.createdAt)
    }

    @Test
    fun `special characters survive round trip`() {
        val tricky = testBooking.copy(
            className = "Laps \"Advanced\" \\ Sprint\nPool",
            instructor = "Jane \"Coach\" O\\Brien\tTab"
        )

        val json = repository.serializeAll(listOf(tricky))
        val parsed = repository.parseBookingsFromJson(json)

        assertEquals(1, parsed.size)
        assertEquals(tricky.className, parsed[0].className)
        assertEquals(tricky.instructor, parsed[0].instructor)

        val viaGson = JsonParser.parseString(json).asJsonArray[0].asJsonObject
        assertEquals(tricky.className, viaGson.get("className").asString)
        assertEquals(tricky.instructor, viaGson.get("instructor").asString)
    }
}
