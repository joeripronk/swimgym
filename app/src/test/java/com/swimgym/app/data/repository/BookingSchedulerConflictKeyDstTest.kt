package com.swimgym.app.data.repository

import android.content.Context
import com.swimgym.app.data.api.VirtuagymApiClient
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.util.AlarmScheduler
import com.swimgym.app.util.BookingNotificationManager
import com.swimgym.app.util.DstTimeFixtures
import com.swimgym.app.util.PermissionHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.FormBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * Conflict warnings for a recurring weekly booking are keyed per week, so that
 * - the same week's conflict is only surfaced once, and
 * - a new week's conflict is surfaced again after the previous week's state resolved.
 *
 * Around a DST transition the occurrence used for the notification and the occurrence
 * used for the "far out" gate must be the same one, so the warning shows the class of
 * the week that is actually being checked.
 */
class BookingSchedulerConflictKeyDstTest {

    private lateinit var scheduler: BookingSchedulerImpl
    private lateinit var context: Context
    private lateinit var notificationManager: BookingNotificationManager
    private lateinit var calendarService: CalendarService
    private lateinit var sessionRepository: SessionRepository
    private lateinit var dao: SwimGymDao
    private lateinit var scheduledBookingRepo: ScheduledBookingRepository
    private lateinit var alarmScheduler: AlarmScheduler

    private lateinit var originalTz: TimeZone
    private val storedNotified = mutableSetOf<String>()

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(DstTimeFixtures.AMSTERDAM))

        context = mockk(relaxUnitFun = true)
        notificationManager = mockk(relaxUnitFun = true)
        calendarService = mockk(relaxUnitFun = true)
        sessionRepository = mockk(relaxUnitFun = true)
        dao = mockk(relaxUnitFun = true)
        scheduledBookingRepo = mockk(relaxUnitFun = true)
        alarmScheduler = mockk(relaxUnitFun = true)

        mockkObject(PermissionHelper)
        every { PermissionHelper.hasCalendarReadPermission(any()) } returns true

        storedNotified.clear()
        coEvery { sessionRepository.getCalendarConflictCheckEnabled() } returns true
        coEvery { sessionRepository.getConflictNotifiedIds() } answers { storedNotified.toSet() }
        coEvery { sessionRepository.saveConflictNotifiedIds(any()) } answers {
            storedNotified.clear()
            storedNotified.addAll(firstArg())
        }
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } answers {
            listOf(CalendarEventInfo(1, "Work meeting", firstArg(), firstArg<Long>() + 3600_000))
        }
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
        apiClient = noopApiClient,
        context = context,
        calendarService = calendarService,
        sessionRepository = sessionRepository,
        nowSeconds = { now }
    )

    private val noopApiClient = object : VirtuagymApiClient {
        override suspend fun fetchHtml(url: String): Document = Jsoup.parse("")
        override suspend fun fetchPage(url: String): String = ""
        override suspend fun postBooking(url: String, body: FormBody): Response = throw UnsupportedOperationException()
        override suspend fun postCancel(url: String, body: FormBody): Response = throw UnsupportedOperationException()
        override suspend fun bookTraining(training: TrainingEntity): Result<Unit> = Result.success(Unit)
        override suspend fun cancelTraining(training: TrainingEntity): Result<Unit> = Result.success(Unit)
        override fun isLoggedIn(): Boolean = true
    }

    private fun trainingAt(localTime: String): TrainingEntity {
        val start = DstTimeFixtures.epochAt(localTime)
        return TrainingEntity(
            id = "t1",
            title = "Laps Swimming",
            instructor = "John",
            instructorLink = "",
            startTime = start,
            endTime = start + 3600,
            location = "SwimGym",
            spotsAvailable = 5,
            isJoined = false,
            isFull = false,
            classTime = "19:00-20:00",
            classDate = localTime.substringBefore(' '),
            imageUrl = "",
            description = "",
            cost = "",
            totalSpots = 10,
            cancelPolicy = "",
            eventId = "",
            calendarId = ""
        )
    }

    private fun bookingAt(localTime: String): ScheduledBooking = ScheduledBooking(
        id = 1L,
        trainingId = "t1",
        className = "Laps Swimming",
        startTime = DstTimeFixtures.epochAt(localTime)
    )

    @Test
    fun `same week conflict is notified once and not re-notified`() = TestScope(UnconfinedTestDispatcher()).runTest {
        val now = DstTimeFixtures.epochAt("2026-10-20 12:00")
        scheduler = schedulerAt(now)
        coEvery { scheduledBookingRepo.getAllBookings() } returns listOf(bookingAt("2026-10-27 19:00"))
        coEvery { dao.getAllTrainingsList() } returns listOf(trainingAt("2026-10-27 19:00"))

        scheduler.checkCalendarConflicts()

        verify(exactly = 1) {
            notificationManager.showScheduledBookingConflictWarning(any(), any(), any(), any(), any(), any())
        }
        assertTrue("warning key bound to the checked week", storedNotified.single().startsWith("c-1-"))

        scheduler.checkCalendarConflicts()

        verify(exactly = 1) {
            notificationManager.showScheduledBookingConflictWarning(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `new week conflict is notified again after the previous week resolved`() = TestScope(UnconfinedTestDispatcher()).runTest {
        // Week 1: conflict on the 2026-10-27 class.
        val nowWeek1 = DstTimeFixtures.epochAt("2026-10-20 12:00")
        scheduler = schedulerAt(nowWeek1)
        coEvery { scheduledBookingRepo.getAllBookings() } returns listOf(bookingAt("2026-10-27 19:00"))
        coEvery { dao.getAllTrainingsList() } returns listOf(trainingAt("2026-10-27 19:00"))

        scheduler.checkCalendarConflicts()
        verify(exactly = 1) {
            notificationManager.showScheduledBookingConflictWarning(any(), any(), any(), any(), any(), any())
        }
        val week1Key = storedNotified.single()

        // Week 1 resolves (no more conflict) -> key is cleared.
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } returns emptyList()
        scheduler.checkCalendarConflicts()
        assertEquals(emptySet<String>(), storedNotified)

        // Week 2: the 2026-11-03 class conflicts -> must be surfaced again.
        coEvery { calendarService.findConflictingEvents(any(), any(), any(), any()) } answers {
            listOf(CalendarEventInfo(1, "Work meeting", firstArg(), firstArg<Long>() + 3600_000))
        }
        val nowWeek2 = DstTimeFixtures.epochAt("2026-10-27 08:00")
        scheduler = schedulerAt(nowWeek2)
        coEvery { scheduledBookingRepo.getAllBookings() } returns listOf(bookingAt("2026-11-03 19:00"))
        coEvery { dao.getAllTrainingsList() } returns listOf(trainingAt("2026-11-03 19:00"))

        scheduler.checkCalendarConflicts()

        verify(exactly = 2) {
            notificationManager.showScheduledBookingConflictWarning(any(), any(), any(), any(), any(), any())
        }
        val week2Key = storedNotified.single()
        assertTrue("week 2 key differs from week 1 key", week2Key != week1Key && week2Key.startsWith("c-1-"))
    }
}
