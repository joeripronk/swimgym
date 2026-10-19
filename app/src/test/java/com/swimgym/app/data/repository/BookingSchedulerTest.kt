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

    private fun invokeFindCalendarConflict(
        startSeconds: Long,
        endSeconds: Long,
        excludeEventId: String?,
        excludeTitle: String
    ): List<CalendarEventInfo> {
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
        val returned = method.invoke(scheduler, startSeconds, endSeconds, excludeEventId, excludeTitle, continuation)
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
        val startTime = now + 3 * 86400

        val result = invokeFindCalendarConflict(startTime, startTime + 3600, null, "Laps Swimming")

        assertEquals(listOf(conflict), result)
    }

    @Test
    fun `findCalendarConflict returns empty when feature disabled`() = testScope.runTest {
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns false
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 3 * 86400

        val result = invokeFindCalendarConflict(startTime, startTime + 3600, null, "Laps Swimming")

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { calendarService.findConflictingEvents(any(), any(), any(), any()) }
    }

    @Test
    fun `findCalendarConflict uses one hour buffer before and after window`() = testScope.runTest {
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 3 * 86400
        val endTime = startTime + 3600

        invokeFindCalendarConflict(startTime, endTime, null, "Laps Swimming")

        coVerify {
            calendarService.findConflictingEvents(
                (startTime - 3600) * 1000,
                (endTime + 3600) * 1000,
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
        val startTime = now + 3 * 86400

        invokeFindCalendarConflict(startTime, startTime + 3600, "42", "Laps Swimming")

        coVerify { calendarService.findConflictingEvents(any(), any(), "42", "Laps Swimming") }
    }

    @Test
    fun `simulateBooking returns Ok when class available and no conflict`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 3 * 86400
        val booking = ScheduledBooking(id = 1L, trainingId = "t1", className = "Laps Swimming", startTime = startTime)
        val training = createTraining("t1", "Laps Swimming", startTime)

        coEvery { dao.getAllTrainingsList() } returns listOf(training)
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()

        val result = scheduler.simulateBooking(booking)

        assertTrue(result is DryRunResult.Ok)
        assertEquals("t1", (result as DryRunResult.Ok).training.id)
        assertTrue(result.bookable)
    }

    @Test
    fun `simulateBooking returns Ok with bookable false when window not open`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 10 * 86400
        val booking = ScheduledBooking(id = 1L, trainingId = "t1", className = "Laps Swimming", startTime = startTime)
        val training = createTraining("t1", "Laps Swimming", startTime)

        coEvery { dao.getAllTrainingsList() } returns listOf(training)
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()

        val result = scheduler.simulateBooking(booking)

        assertTrue(result is DryRunResult.Ok)
        assertFalse((result as DryRunResult.Ok).bookable)
    }

    @Test
    fun `simulateBooking returns Renamed when class title changed`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 10 * 86400
        val booking = ScheduledBooking(id = 1L, trainingId = "t1", className = "Laps Swimming", startTime = startTime)
        val training = createTraining("t1", "Advanced Laps", startTime)

        coEvery { dao.getAllTrainingsList() } returns listOf(training)

        val result = scheduler.simulateBooking(booking)

        assertTrue(result is DryRunResult.Renamed)
        assertEquals("Advanced Laps", (result as DryRunResult.Renamed).newName)
    }

    @Test
    fun `simulateBooking returns NotAvailable when class not in schedule`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 10 * 86400
        val booking = ScheduledBooking(id = 1L, trainingId = "t1", className = "Laps Swimming", startTime = startTime)

        coEvery { dao.getAllTrainingsList() } returns emptyList()

        val result = scheduler.simulateBooking(booking)

        assertTrue(result is DryRunResult.NotAvailable)
    }

    @Test
    fun `simulateBooking returns Conflict when calendar event overlaps`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 10 * 86400
        val booking = ScheduledBooking(id = 1L, trainingId = "t1", className = "Laps Swimming", startTime = startTime)
        val training = createTraining("t1", "Laps Swimming", startTime)
        val conflict = CalendarEventInfo(
            id = 5,
            title = "Work meeting",
            startMillis = startTime * 1000,
            endMillis = (startTime + 3600) * 1000
        )

        coEvery { dao.getAllTrainingsList() } returns listOf(training)
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns listOf(conflict)

        val result = scheduler.simulateBooking(booking)

        assertTrue(result is DryRunResult.Conflict)
        assertEquals("Work meeting", (result as DryRunResult.Conflict).conflictTitle)
        assertFalse(result.bookable)
    }

    @Test
    fun `processSingleBooking books despite calendar conflict`() = testScope.runTest {
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
        apiClient.bookResult = Result.success(Unit)

        val result = scheduler.processSingleBooking(1L)

        assertTrue(result)
        assertTrue(apiClient.bookTrainingCalled)
        verify(exactly = 0) { notificationManager.showScheduledBookingConflictWarning(any(), any(), any(), any(), any(), any()) }
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
        verify(exactly = 0) { notificationManager.showScheduledBookingConflictWarning(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `checkCalendarConflicts warns when scheduled booking more than 7 days in future collides`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 10 * 86400
        val booking = ScheduledBooking(
            id = 1L,
            trainingId = "t1",
            className = "Laps Swimming",
            startTime = startTime,
            instructor = "John"
        )
        val training = createTraining("t1", "Laps Swimming", startTime)
        val conflict = CalendarEventInfo(
            id = 5,
            title = "Work meeting",
            startMillis = startTime * 1000,
            endMillis = (startTime + 3600) * 1000
        )

        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { scheduledBookingRepo.getAllBookings() } returns listOf(booking)
        coEvery { dao.getAllTrainingsList() } returns listOf(training)
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns listOf(conflict)
        coEvery { sessionRepository.getConflictNotifiedIds() } returns emptySet()

        scheduler.checkCalendarConflicts()

        verify {
            notificationManager.showScheduledBookingConflictWarning(
                1L,
                "Laps Swimming",
                training.classDate,
                training.classTime,
                "John",
                "Work meeting"
            )
        }
        coVerify { sessionRepository.saveConflictNotifiedIds(setOf("c-1")) }
    }

    @Test
    fun `checkCalendarConflicts does not warn when booking within 7 days`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 3 * 86400
        val booking = ScheduledBooking(
            id = 1L,
            trainingId = "t1",
            className = "Laps Swimming",
            startTime = startTime,
            instructor = "John"
        )
        val training = createTraining("t1", "Laps Swimming", startTime)

        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { scheduledBookingRepo.getAllBookings() } returns listOf(booking)
        coEvery { dao.getAllTrainingsList() } returns listOf(training)
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns listOf(
            CalendarEventInfo(id = 5, title = "Work meeting", startMillis = startTime * 1000, endMillis = (startTime + 3600) * 1000)
        )
        coEvery { sessionRepository.getConflictNotifiedIds() } returns emptySet()

        scheduler.checkCalendarConflicts()

        coVerify(exactly = 0) { calendarService.findConflictingEvents(any(), any(), any(), any()) }
        verify(exactly = 0) { notificationManager.showScheduledBookingConflictWarning(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `checkCalendarConflicts does not re-notify already notified bookings`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 10 * 86400
        val booking = ScheduledBooking(
            id = 1L,
            trainingId = "t1",
            className = "Laps Swimming",
            startTime = startTime,
            instructor = "John"
        )
        val training = createTraining("t1", "Laps Swimming", startTime)

        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { scheduledBookingRepo.getAllBookings() } returns listOf(booking)
        coEvery { dao.getAllTrainingsList() } returns listOf(training)
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns listOf(
            CalendarEventInfo(id = 5, title = "Work meeting", startMillis = startTime * 1000, endMillis = (startTime + 3600) * 1000)
        )
        coEvery { sessionRepository.getConflictNotifiedIds() } returns setOf("c-1")

        scheduler.checkCalendarConflicts()

        verify(exactly = 0) { notificationManager.showScheduledBookingConflictWarning(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `checkCalendarConflicts notifies when scheduled booking not available`() = testScope.runTest {
        val now = System.currentTimeMillis() / 1000
        val startTime = now + 10 * 86400
        val booking = ScheduledBooking(
            id = 1L,
            trainingId = "t1",
            className = "Laps Swimming",
            startTime = startTime,
            instructor = "John"
        )

        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { scheduledBookingRepo.getAllBookings() } returns listOf(booking)
        coEvery { dao.getAllTrainingsList() } returns emptyList()
        coEvery { sessionRepository.getConflictNotifiedIds() } returns emptySet()

        scheduler.checkCalendarConflicts()

        verify { notificationManager.showScheduledBookingNotAvailable(1L, "Laps Swimming", "no cached trainings") }
        coVerify { sessionRepository.saveConflictNotifiedIds(setOf("na-1")) }
    }
}
