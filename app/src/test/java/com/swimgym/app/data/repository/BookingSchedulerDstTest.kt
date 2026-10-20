package com.swimgym.app.data.repository

import android.content.Context
import com.swimgym.app.data.api.VirtuagymApiClient
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.util.AlarmScheduler
import com.swimgym.app.util.BookingNotificationManager
import com.swimgym.app.util.DstTimeFixtures
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.FormBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * The booking window ("you may book a class from 7 days before it starts, until the class
 * starts") is expressed in *local* wall-clock time by the gym, while the app compares
 * absolute epoch seconds. 7 local days are 6d23h before the spring transition and 7d1h
 * after the autumn transition, so a class that is exactly 7 local days away can fall
 * outside the 7 * 24h window the app uses.
 *
 * These tests pin the behaviour down around both transitions.
 */
class BookingSchedulerDstTest {

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

    private lateinit var originalTz: TimeZone

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(DstTimeFixtures.AMSTERDAM))

        context = mockk(relaxUnitFun = true)
        notificationManager = mockk(relaxUnitFun = true)
        calendarService = mockk(relaxUnitFun = true)
        sessionRepository = mockk(relaxUnitFun = true)
        apiClient = FakeVirtuagymApiClient()
        dao = mockk(relaxUnitFun = true)
        scheduledBookingRepo = mockk(relaxUnitFun = true)
        alarmScheduler = mockk(relaxUnitFun = true)
        testScope = TestScope(UnconfinedTestDispatcher())
        mockkObject(com.swimgym.app.util.PermissionHelper)
        every { com.swimgym.app.util.PermissionHelper.hasCalendarReadPermission(any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
        TimeZone.setDefault(originalTz)
    }

    private fun schedulerAt(now: Long): BookingSchedulerImpl = BookingSchedulerImpl(
        dao = dao,
        scheduledBookingRepo = scheduledBookingRepo,
        notificationManager = notificationManager,
        alarmScheduler = alarmScheduler,
        apiClient = apiClient,
        context = context,
        calendarService = calendarService,
        sessionRepository = sessionRepository,
        nowSeconds = { now }
    )

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

    private fun createTraining(id: String, title: String, startTime: Long): TrainingEntity =
        TrainingEntity(
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
            classTime = "19:00-20:00",
            classDate = "27-10-2026",
            imageUrl = "",
            description = "",
            cost = "",
            totalSpots = 10,
            cancelPolicy = "",
            eventId = "",
            calendarId = ""
        )

    private fun trainingDetailsDocument(title: String, spots: String = "3/10"): Document = Jsoup.parse(
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

    private fun bookingFor(classLocalTime: String, id: Long = 1L): ScheduledBooking =
        ScheduledBooking(
            id = id,
            trainingId = "t1",
            className = "Laps Swimming",
            startTime = DstTimeFixtures.epochAt(classLocalTime)
        )

    // -------------------------------------------------------------------------------------
    // The alarm fires 5 minutes after the booking window opens: 7 local days before the class.
    // -------------------------------------------------------------------------------------

    @Test
    fun `class exactly 7 local days ahead is bookable when the autumn transition is in between`() = testScope.runTest {
        // The alarm for the class of 2026-10-27 19:00 fires at 2026-10-20 19:05 local.
        // The transition happens on 2026-10-25, so in absolute time the class is
        // 7 days + 55 minutes away, which still is "7 days" in the gym's wall-clock world.
        val now = DstTimeFixtures.epochAt("2026-10-20 19:05")
        val classTime = DstTimeFixtures.epochAt("2026-10-27 19:00")
        scheduler = schedulerAt(now)
        coEvery { dao.getAllTrainingsList() } returns listOf(createTraining("t1", "Laps Swimming", classTime))
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()

        val result = scheduler.simulateBooking(bookingFor("2026-10-27 19:00"))

        assertTrue("expected the class to be found in the schedule", result is DryRunResult.Ok)
        assertTrue("expected the class to be bookable", (result as DryRunResult.Ok).bookable)
    }

    @Test
    fun `class exactly 7 local days ahead is bookable when the spring transition is in between`() = testScope.runTest {
        // The alarm for the class of 2026-03-29 19:00 fires at 2026-03-22 19:05 local.
        val now = DstTimeFixtures.epochAt("2026-03-22 19:05")
        val classTime = DstTimeFixtures.epochAt("2026-03-29 19:00")
        scheduler = schedulerAt(now)
        coEvery { dao.getAllTrainingsList() } returns listOf(createTraining("t1", "Laps Swimming", classTime))
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()

        val result = scheduler.simulateBooking(bookingFor("2026-03-29 19:00"))

        assertTrue("expected the class to be found in the schedule", result is DryRunResult.Ok)
        assertTrue("expected the class to be bookable", (result as DryRunResult.Ok).bookable)
    }

    @Test
    fun `class 8 local days ahead is not bookable`() = testScope.runTest {
        val now = DstTimeFixtures.epochAt("2026-10-19 19:05")
        val classTime = DstTimeFixtures.epochAt("2026-10-27 19:00")
        scheduler = schedulerAt(now)
        coEvery { dao.getAllTrainingsList() } returns listOf(createTraining("t1", "Laps Swimming", classTime))
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()

        val result = scheduler.simulateBooking(bookingFor("2026-10-27 19:00"))

        assertTrue("expected the class to be found in the schedule", result is DryRunResult.Ok)
        assertFalse("expected the class to be too far ahead", (result as DryRunResult.Ok).bookable)
    }

    @Test
    fun `class 3 local days ahead is bookable when the autumn transition is in between`() = testScope.runTest {
        val now = DstTimeFixtures.epochAt("2026-10-24 19:05")
        val classTime = DstTimeFixtures.epochAt("2026-10-27 19:00")
        scheduler = schedulerAt(now)
        coEvery { dao.getAllTrainingsList() } returns listOf(createTraining("t1", "Laps Swimming", classTime))
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()

        val result = scheduler.simulateBooking(bookingFor("2026-10-27 19:00"))

        assertTrue("expected the class to be found in the schedule", result is DryRunResult.Ok)
        assertTrue("expected the class to be bookable", (result as DryRunResult.Ok).bookable)
    }

    @Test
    fun `class 1 local day ahead is not bookable`() = testScope.runTest {
        val now = DstTimeFixtures.epochAt("2026-10-26 19:05")
        val classTime = DstTimeFixtures.epochAt("2026-10-27 19:00")
        scheduler = schedulerAt(now)
        coEvery { dao.getAllTrainingsList() } returns listOf(createTraining("t1", "Laps Swimming", classTime))
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()

        val result = scheduler.simulateBooking(bookingFor("2026-10-27 19:00"))

        assertTrue("expected the class to be found in the schedule", result is DryRunResult.Ok)
        assertFalse("expected the class to be too close", (result as DryRunResult.Ok).bookable)
    }

    // -------------------------------------------------------------------------------------
    // The end-to-end path: the alarm fires and the booking should happen.
    // -------------------------------------------------------------------------------------

    @Test
    fun `booking is made when the alarm fires 7 local days before the class across the autumn transition`() = testScope.runTest {
        val now = DstTimeFixtures.epochAt("2026-10-20 19:05")
        val classTime = DstTimeFixtures.epochAt("2026-10-27 19:00")
        scheduler = schedulerAt(now)
        val booking = bookingFor("2026-10-27 19:00")
        coEvery { scheduledBookingRepo.getBooking(1L) } returns booking
        coEvery { dao.getAllTrainingsList() } returns listOf(createTraining("t1", "Laps Swimming", classTime))
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()
        apiClient.htmlToReturn = trainingDetailsDocument("Laps Swimming")
        apiClient.bookResult = Result.success(Unit)

        val result = scheduler.processSingleBooking(1L)

        assertTrue("expected the booking to succeed", result)
        assertTrue("expected the class to be booked", apiClient.bookTrainingCalled)
    }

    @Test
    fun `booking is made when the alarm fires 7 local days before the class across the spring transition`() = testScope.runTest {
        val now = DstTimeFixtures.epochAt("2026-03-22 19:05")
        val classTime = DstTimeFixtures.epochAt("2026-03-29 19:00")
        scheduler = schedulerAt(now)
        val booking = bookingFor("2026-03-29 19:00")
        coEvery { scheduledBookingRepo.getBooking(1L) } returns booking
        coEvery { dao.getAllTrainingsList() } returns listOf(createTraining("t1", "Laps Swimming", classTime))
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()
        apiClient.htmlToReturn = trainingDetailsDocument("Laps Swimming")
        apiClient.bookResult = Result.success(Unit)

        val result = scheduler.processSingleBooking(1L)

        assertTrue("expected the booking to succeed", result)
        assertTrue("expected the class to be booked", apiClient.bookTrainingCalled)
    }

    @Test
    fun `weekly sync books the class even when the alarm moment is skipped`() = testScope.runTest {
        // The 21:00 sync on 2026-10-20 sees the class of 2026-10-27 as 6d23h away, so it is
        // still inside the 7 day window and the class is booked.
        val now = DstTimeFixtures.epochAt("2026-10-20 21:00")
        val classTime = DstTimeFixtures.epochAt("2026-10-27 19:00")
        scheduler = schedulerAt(now)
        val booking = bookingFor("2026-10-27 19:00")
        coEvery { scheduledBookingRepo.getAllBookings() } returns listOf(booking)
        coEvery { dao.getAllTrainingsList() } returns listOf(createTraining("t1", "Laps Swimming", classTime))
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()
        apiClient.htmlToReturn = trainingDetailsDocument("Laps Swimming")
        apiClient.bookResult = Result.success(Unit)

        scheduler.checkBookings()

        assertTrue("expected the class to be booked by the sync", apiClient.bookTrainingCalled)
    }

    @Test
    fun `the next occurrence after a booking keeps the local class time across the autumn transition`() = testScope.runTest {        val now = DstTimeFixtures.epochAt("2026-10-20 19:05")
        val classTime = DstTimeFixtures.epochAt("2026-10-27 19:00")
        scheduler = schedulerAt(now)
        val booking = bookingFor("2026-10-27 19:00")
        val saved = mutableListOf<ScheduledBooking>()
        coEvery { scheduledBookingRepo.getBooking(1L) } returns booking
        coEvery { scheduledBookingRepo.saveBooking(capture(saved)) } returns Unit
        coEvery { scheduledBookingRepo.incrementBookingCount(any(), any()) } returns Unit
        coEvery { dao.getAllTrainingsList() } returns listOf(createTraining("t1", "Laps Swimming", classTime))
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()
        apiClient.htmlToReturn = trainingDetailsDocument("Laps Swimming")
        apiClient.bookResult = Result.success(Unit)

        scheduler.processSingleBooking(1L)

        // the booking is rolled forward by 7 local days, so the next class is at 19:00 local
        assertEquals("2026-11-03 19:00", DstTimeFixtures.local(saved.last().startTime))
    }

    @Test
    fun `a failed booking is retried 15 minutes later while the window is open across the autumn transition`() = testScope.runTest {
        // The class of 2026-10-27 19:00 is 6d23h55m away in local time, so the booking
        // window is open and the booking has to be retried shortly.
        val now = DstTimeFixtures.epochAt("2026-10-20 19:05")
        val classTime = DstTimeFixtures.epochAt("2026-10-27 19:00")
        scheduler = schedulerAt(now)
        val booking = bookingFor("2026-10-27 19:00")
        coEvery { scheduledBookingRepo.getBooking(1L) } returns booking
        coEvery { dao.getAllTrainingsList() } returns listOf(createTraining("t1", "Laps Swimming", classTime))
        coEvery { dao.getTrainingById("t1") } returns null
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()
        apiClient.htmlToReturn = trainingDetailsDocument("Laps Swimming")
        apiClient.bookResult = Result.failure(Exception("boom"))

        scheduler.processSingleBooking(1L)

        val requested = mutableListOf<Long>()
        coEvery { alarmScheduler.scheduleBooking(1L, capture(requested)) } returns Unit
        scheduler.processSingleBooking(1L)

        assertTrue("expected a retry alarm", requested.isNotEmpty())
        assertEquals("2026-10-20 19:20", DstTimeFixtures.local(requested.last() / 1000))
    }

    @Test
    fun `a class 7 local days ahead is not treated as a far out conflict across the autumn transition`() = testScope.runTest {
        // The class of 2026-10-27 19:00 is 6d23h55m away in local time: still inside the
        // booking window, so it is not a "far out" booking that needs a conflict warning.
        val now = DstTimeFixtures.epochAt("2026-10-20 19:05")
        val classTime = DstTimeFixtures.epochAt("2026-10-27 19:00")
        scheduler = schedulerAt(now)
        val booking = bookingFor("2026-10-27 19:00")
        coEvery { scheduledBookingRepo.getAllBookings() } returns listOf(booking)
        coEvery { dao.getAllTrainingsList() } returns listOf(createTraining("t1", "Laps Swimming", classTime))
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns listOf(
            CalendarEventInfo(
                id = 5,
                title = "Work meeting",
                startMillis = classTime * 1000,
                endMillis = (classTime + 3600) * 1000
            )
        )
        coEvery { sessionRepository.getConflictNotifiedIds() } returns emptySet()

        scheduler.checkCalendarConflicts()

        io.mockk.verify(exactly = 0) {
            notificationManager.showScheduledBookingConflictWarning(any(), any(), any(), any(), any(), any())
        }
    }
}
