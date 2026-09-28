package com.swimgym.app.data.repository

import android.content.Context
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.api.VirtuagymApiClient
import com.swimgym.app.util.AlarmScheduler
import com.swimgym.app.util.BookingNotificationManager
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.FormBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.slot

class BookingSchedulerTest {

    private lateinit var scheduler: BookingSchedulerImpl
    private lateinit var context: Context
    private lateinit var testScope: TestScope

    private lateinit var notificationManager: BookingNotificationManager
    private lateinit var calendarService: CalendarService
    private lateinit var sessionRepository: SessionRepository
    private lateinit var apiClient: FakeVirtuagymApiClient
    private lateinit var dao: SwimGymDao
    private lateinit var scheduledBookingRepo: ScheduledBookingRepository
    private lateinit var alarmScheduler: AlarmScheduler

    @Before
    fun setUp() {
        context = mockk(relaxUnitFun = true)
        notificationManager = mockk(relaxUnitFun = true)
        calendarService = mockk(relaxUnitFun = true)
        sessionRepository = mockk(relaxUnitFun = true)
        apiClient = FakeVirtuagymApiClient()
        dao = mockk(relaxUnitFun = true)
        scheduledBookingRepo = mockk(relaxUnitFun = true)
        alarmScheduler = mockk(relaxUnitFun = true)
        scheduler = BookingSchedulerImpl(
            dao = dao,
            scheduledBookingRepo = scheduledBookingRepo,
            notificationManager = notificationManager,
            alarmScheduler = alarmScheduler,
            apiClient = apiClient,
            context = context,
            calendarService = calendarService,
            sessionRepository = sessionRepository
        )
        testScope = TestScope(UnconfinedTestDispatcher())
        mockkObject(com.swimgym.app.util.PermissionHelper)
        every { com.swimgym.app.util.PermissionHelper.hasCalendarReadPermission(any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private class FakeVirtuagymApiClient : VirtuagymApiClient {
        var htmlToReturn: Document = Jsoup.parse("")
        var bookResult: Result<Unit> = Result.success(Unit)
        var bookTrainingCalled = false

        override suspend fun fetchHtml(url: String): Document = htmlToReturn
        override suspend fun fetchPage(url: String): String = ""
        override suspend fun postBooking(url: String, body: FormBody): Response = throw UnsupportedOperationException()
        override suspend fun postCancel(url: String, body: FormBody): Response = throw UnsupportedOperationException()
        override suspend fun bookTraining(training: TrainingEntity): Result<Unit> {
            bookTrainingCalled = true
            return bookResult
        }
        override suspend fun cancelTraining(training: TrainingEntity): Result<Unit> = bookResult
        override fun isLoggedIn(): Boolean = true
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

        verify { notificationManager.showTrainingRenamed("Laps Swimming", "Advanced Laps") }
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

    private fun invokeFindCalendarConflict(training: TrainingEntity): List<CalendarEventInfo> {
        var asyncResult: List<CalendarEventInfo>? = null
        val method = BookingSchedulerImpl::class.java.declaredMethods.first { it.name == "findCalendarConflict" }
        method.isAccessible = true
        val continuation = object : Continuation<List<CalendarEventInfo>> {
            override val context: CoroutineContext = EmptyCoroutineContext
            override fun resumeWith(result: Result<List<CalendarEventInfo>>) {
                asyncResult = result.getOrThrow()
            }
        }
        @Suppress("UNCHECKED_CAST")
        val returned = method.invoke(scheduler, training, continuation)
        return if (returned === COROUTINE_SUSPENDED) {
            checkNotNull(asyncResult)
        } else {
            returned as List<CalendarEventInfo>
        }
    }

    private fun trainingDetailsDocument(title: String, spots: String = "3/10"): Document {
        return Jsoup.parse(
            """
            <div>
              <div class="event-details-icons">
                <div class="event-details-icon"><div class="icon-text">info</div></div>
              </div>
              <div class="event-details-icons">
                <div class="event-details-icon"><div class="icon-text">info</div></div>
                <div class="event-details-icon"><div class="icon-text">info</div></div>
                <div class="event-details-icon"><div class="icon-text">info</div><div class="icon-text">$spots</div></div>
              </div>
              <div class="modal-title-replacement">$title</div>
            </div>
            """.trimIndent()
        )
    }

    @Test
    fun `findCalendarConflict returns conflicts from calendar`() = testScope.runTest {
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        val conflict = CalendarEventInfo(id = 5, title = "Work meeting", startMillis = 1000, endMillis = 2000)
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns listOf(conflict)
        val now = System.currentTimeMillis() / 1000
        val training = createTraining("t1", "Laps Swimming", now + 3 * 86400)

        val result = invokeFindCalendarConflict(training)

        assertEquals(listOf(conflict), result)
    }

    @Test
    fun `findCalendarConflict returns empty when feature disabled`() = testScope.runTest {
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns false
        val now = System.currentTimeMillis() / 1000
        val training = createTraining("t1", "Laps Swimming", now + 3 * 86400)

        val result = invokeFindCalendarConflict(training)

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { calendarService.findConflictingEvents(any(), any(), any(), any()) }
    }

    @Test
    fun `findCalendarConflict uses one hour buffer before and after training`() = testScope.runTest {
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 3 * 86400
        val training = createTraining("t1", "Laps Swimming", startTime)

        invokeFindCalendarConflict(training)

        coVerify {
            calendarService.findConflictingEvents(
                (startTime - 3600) * 1000,
                (startTime + 3600 + 3600) * 1000,
                null,
                "Laps Swimming"
            )
        }
    }

    @Test
    fun `findCalendarConflict excludes own calendar event id`() = testScope.runTest {
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()
        val now = System.currentTimeMillis() / 1000
        val training = createTraining("t1", "Laps Swimming", now + 3 * 86400).copy(eventId = "42")

        invokeFindCalendarConflict(training)

        coVerify { calendarService.findConflictingEvents(any(), any(), "42", "Laps Swimming") }
    }

    @Test
    fun `processSingleBooking skips booking when calendar conflict is detected`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 3 * 86400
        val booking = ScheduledBooking(id = 1L, trainingId = "t1", className = "Laps Swimming", startTime = startTime)
        val training = createTraining("t1", "Laps Swimming", startTime)
        val conflict = CalendarEventInfo(
            id = 5,
            title = "Work meeting",
            startMillis = startTime * 1000,
            endMillis = (startTime + 3600) * 1000
        )

        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { scheduledBookingRepo.getBooking(1L) } returns booking
        coEvery { dao.getAllTrainingsList() } returns listOf(training)
        apiClient.htmlToReturn = trainingDetailsDocument("Laps Swimming")
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns listOf(conflict)

        val result = scheduler.processSingleBooking(1L)

        assertTrue(result)
        assertFalse(apiClient.bookTrainingCalled)
        verify {
            notificationManager.showBookingSkippedDueToConflict(
                "Laps Swimming",
                training.classDate,
                training.classTime,
                "John",
                "Work meeting"
            )
        }
        coVerify(exactly = 2) { scheduledBookingRepo.saveBooking(any()) }
        coVerify(exactly = 0) { scheduledBookingRepo.pauseBooking(any()) }
        coVerify(exactly = 0) { alarmScheduler.scheduleBooking(any(), any()) }
    }

    @Test
    fun `processSingleBooking books when no calendar conflict`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 3 * 86400
        val booking = ScheduledBooking(id = 1L, trainingId = "t1", className = "Laps Swimming", startTime = startTime)
        val training = createTraining("t1", "Laps Swimming", startTime)

        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { scheduledBookingRepo.getBooking(1L) } returns booking
        coEvery { dao.getAllTrainingsList() } returns listOf(training)
        apiClient.htmlToReturn = trainingDetailsDocument("Laps Swimming")
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()
        apiClient.bookResult = Result.success(Unit)

        val result = scheduler.processSingleBooking(1L)

        assertTrue(result)
        assertTrue(apiClient.bookTrainingCalled)
        verify(exactly = 0) { notificationManager.showBookingSkippedDueToConflict(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `processSingleBooking skips when already marked as skipped due to conflict`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 3 * 86400
        val booking = ScheduledBooking(
            id = 1L,
            trainingId = "t1",
            className = "Laps Swimming",
            startTime = startTime,
            skippedDueToConflict = true
        )

        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { scheduledBookingRepo.getBooking(1L) } returns booking
        coEvery { dao.getAllTrainingsList() } returns emptyList()

        val savedBookings = mutableListOf<ScheduledBooking>()
        coEvery { scheduledBookingRepo.saveBooking(any()) } coAnswers { savedBookings.add(firstArg()) }

        val result = scheduler.processSingleBooking(1L)

        assertTrue(result)
        assertFalse(apiClient.bookTrainingCalled)
        coVerify(exactly = 0) { dao.getAllTrainingsList() }
        // flag should be cleared so next week the scheduler tries again
        assertFalse(savedBookings.last().skippedDueToConflict)
    }

    @Test
    fun `processSingleBooking clears skippedDueToConflict flag on successful booking`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 3 * 86400
        val training = createTraining("t1", "Laps Swimming", startTime)
        val booking = ScheduledBooking(
            id = 1L,
            trainingId = "t1",
            className = "Laps Swimming",
            startTime = startTime,
            skippedDueToConflict = false
        )

        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { scheduledBookingRepo.getBooking(1L) } returns booking
        coEvery { dao.getAllTrainingsList() } returns listOf(training)
        apiClient.htmlToReturn = trainingDetailsDocument("Laps Swimming")
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()
        apiClient.bookResult = Result.success(Unit)

        val savedBookings = mutableListOf<ScheduledBooking>()
        coEvery { scheduledBookingRepo.saveBooking(any()) } coAnswers { savedBookings.add(firstArg()) }

        scheduler.processSingleBooking(1L)

        // booking should succeed and flag should remain false
        assertFalse(savedBookings.last().skippedDueToConflict)
        assertEquals(1, savedBookings.last().bookedCount)
    }
}
