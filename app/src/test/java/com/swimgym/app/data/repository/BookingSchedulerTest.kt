package com.swimgym.app.data.repository

import android.content.Context
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.util.AlarmScheduler
import com.swimgym.app.util.BookingNotificationManager
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class BookingSchedulerTest {

    private lateinit var scheduler: BookingSchedulerImpl
    private lateinit var context: Context
    private lateinit var testScope: TestScope

    private lateinit var notificationManager: BookingNotificationManager

    @Before
    fun setUp() {
        context = mock()
        notificationManager = mock()
        scheduler = BookingSchedulerImpl(
            dao = mock(),
            scheduledBookingRepo = mock(),
            notificationManager = notificationManager,
            alarmScheduler = mock(),
            apiClient = mock(),
            context = context
        )
        testScope = TestScope(UnconfinedTestDispatcher())
    }

    private fun createTraining(id: String, title: String, startTime: Long): TrainingEntity {
        return TrainingEntity(
            id = id,
            title = title,
            instructor = "John",
            instructorLink = "",
            startTime = startTime,
            endTime = startTime + 3600,
            location = "SwimGym",
            spotsAvailable = 5,
            isJoined = false,
            isFull = false,
            classTime = "18:00-19:00",
            classDate = "19-09-2025",
            imageUrl = "",
            description = "",
            cost = "",
            totalSpots = 10,
            cancelPolicy = "",
            eventId = "",
            calendarId = ""
        )
    }

    private fun getNextTraining(startTime: Long, title: String, trainings: List<TrainingEntity>): TrainingEntity? {
        val method = BookingSchedulerImpl::class.java.getDeclaredMethod(
            "getNextTraining",
            Long::class.java,
            String::class.java,
            List::class.java,
            BookingNotificationManager::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(scheduler, startTime, title, trainings, notificationManager) as? TrainingEntity
    }

    @Test
    fun `getNextTraining returns training when title matches`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val nextTime = now + 3 * 86400
        val training = createTraining("t1", "Laps Swimming", nextTime)

        val result = getNextTraining(nextTime, "Laps Swimming", listOf(training))
        assertNotNull(result)
        assertEquals("Laps Swimming", result?.title)
    }

    @Test
    fun `getNextTraining returns null when title does not match`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val nextTime = now + 3 * 86400
        val training = createTraining("t1", "Water Polo", nextTime)

        val result = getNextTraining(nextTime, "Laps Swimming", listOf(training))
        assertNull(result)
    }

    @Test
    fun `getNextTraining shows renamed notification when title mismatch`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val nextTime = now + 3 * 86400
        val training = createTraining("t1", "Advanced Laps", nextTime)

        getNextTraining(nextTime, "Laps Swimming", listOf(training))

        verify(notificationManager).showTrainingRenamed(eq("Laps Swimming"), eq("Advanced Laps"))
    }

    @Test
    fun `getNextTraining returns null for training within 2 days`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val nextTime = now + 86400 // 1 day from now
        val training = createTraining("t1", "Laps Swimming", nextTime)

        val result = getNextTraining(nextTime, "Laps Swimming", listOf(training))
        assertNull(result)
    }

    @Test
    fun `getNextTraining returns null for training more than 1 week away`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val nextTime = now + 10 * 86400 // 10 days from now
        val training = createTraining("t1", "Laps Swimming", nextTime)

        val result = getNextTraining(nextTime, "Laps Swimming", listOf(training))
        assertNull(result)
    }

    @Test
    fun `getNextTraining case insensitive title match`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val nextTime = now + 3 * 86400
        val training = createTraining("t1", "laps swimming", nextTime)

        val result = getNextTraining(nextTime, "LAPS SWIMMING", listOf(training))
        assertNotNull(result)
    }

    @Test
    fun `getNextTraining returns null when no trainings in list`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val nextTime = now + 3 * 86400

        val result = getNextTraining(nextTime, "Laps Swimming", emptyList())
        assertNull(result)
    }

    @Test
    fun `getNextTraining finds match among multiple trainings`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val nextTime = now + 3 * 86400
        val training1 = createTraining("t1", "Water Polo", nextTime)
        val training2 = createTraining("t2", "Laps Swimming", nextTime)

        val result = getNextTraining(nextTime, "Laps Swimming", listOf(training1, training2))
        assertNotNull(result)
        assertEquals("t2", result?.id)
    }

    @Test
    fun `getNextTraining returns first matching training`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val nextTime = now + 3 * 86400
        val training1 = createTraining("t1", "Laps Swimming", nextTime)
        val training2 = createTraining("t2", "Laps Swimming", nextTime)

        val result = getNextTraining(nextTime, "Laps Swimming", listOf(training1, training2))
        assertNotNull(result)
        assertEquals("t1", result?.id)
    }

    @Test
    fun `getNextTraining returns null when matching training has different time`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val nextTime = now + 3 * 86400
        val training = createTraining("t1", "Laps Swimming", nextTime + 7 * 86400)

        val result = getNextTraining(nextTime, "Laps Swimming", listOf(training))
        assertNull(result)
    }
}
