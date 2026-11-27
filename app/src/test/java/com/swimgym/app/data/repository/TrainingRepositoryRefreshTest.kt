package com.swimgym.app.data.repository

import com.swimgym.app.data.model.Mappers.toEntity
import com.swimgym.app.data.api.VirtuagymApiClient
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.model.TrainingDto
import com.swimgym.app.data.parser.ScheduleParser
import com.swimgym.app.domain.repository.SwodLevel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingRepositoryRefreshTest {

    private val api = mockk<VirtuagymApiClient>()
    private val dao = mockk<SwimGymDao>(relaxUnitFun = true)
    private val parser = mockk<ScheduleParser>()
    private val document = mockk<Document>()

    private fun repository() = TrainingRepositoryImpl(
        apiClient = api,
        dao = dao,
        parser = parser,
        calendarService = mockk(relaxUnitFun = true)
    )

    private fun training(id: String, eventType: Int = ScheduleFeeds.SWOD) = TrainingDto(
        id = id,
        eventType = eventType,
        title = "Laps Swimming",
        instructor = "John",
        startTime = 1700000000L,
        endTime = 1700003600L
    )

    private fun batchResults(size: Int, failedIndex: Int? = null): List<Result<Document>> =
        (0 until size).map { index ->
            if (index == failedIndex) Result.failure(Exception("feed failed"))
            else Result.success(document)
        }

    @Test
    fun `partial fetch does not delete cached classes from a failed feed`() = runTest {
        coEvery { api.fetchHtmlBatch(any()) } returns batchResults(8, failedIndex = 2)
        var calls = 0
        coEvery { parser.parseSchedule(any(), any(), any(), any(), any()) } answers {
            calls++
            listOf(training("week-$calls"))
        }
        coEvery { dao.getAllTrainingIds() } returns listOf("week-1", "week-2", "week-3")

        val result = repository().refreshSchedule()

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { dao.insertTrainings(any()) }
        coVerify(exactly = 0) { dao.deleteTrainingById(any()) }
    }

    @Test
    fun `partial fetch on a cold cache still returns the classes that were saved`() = runTest {
        coEvery { api.fetchHtmlBatch(any()) } returns batchResults(8, failedIndex = 2)
        coEvery { parser.parseSchedule(any(), any(), any(), any(), any()) } answers {
            listOf(training("parsed"))
        }
        var cacheReads = 0
        coEvery { dao.getAllTrainingsList() } answers {
            cacheReads++
            if (cacheReads == 1) {
                emptyList()
            } else {
                listOf(training("week-1").toEntity(), training("week-3").toEntity())
            }
        }
        coEvery { dao.getAllTrainingIds() } returns emptyList()

        val result = repository().getSchedule(SwodLevel.ALL, false, null)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun `total fetch failure leaves the cache untouched and reports failure`() = runTest {
        coEvery { api.fetchHtmlBatch(any()) } returns List(8) {
            Result.failure<Document>(Exception("offline"))
        }
        coEvery { dao.getAllTrainingIds() } returns listOf("cached-1")

        val result = repository().refreshSchedule()

        assertFalse(result.isSuccess)
        coVerify(exactly = 0) { dao.insertTrainings(any()) }
        coVerify(exactly = 0) { dao.deleteTrainingById(any()) }
    }

    @Test
    fun `schedule fetch requests both SWOD and Laps feeds for every week`() = runTest {
        val urls = slot<List<String>>()
        val eventTypes = mutableListOf<Int>()
        coEvery { api.fetchHtmlBatch(capture(urls)) } returns batchResults(8)
        coEvery { parser.parseSchedule(any(), any(), any(), any(), capture(eventTypes)) } answers {
            listOf(training("parsed"))
        }
        coEvery { dao.getAllTrainingIds() } returns emptyList()

        repository().refreshSchedule()

        val fetched = urls.captured
        assertEquals(8, fetched.size)
        assertEquals(4, fetched.count { it.contains("event_type=${ScheduleFeeds.SWOD}") })
        assertEquals(4, fetched.count { it.contains("event_type=${ScheduleFeeds.LAPS}") })
        assertEquals(4, eventTypes.count { it == ScheduleFeeds.LAPS })
    }
}
