package com.swimgym.app.data.repository

import android.content.Context
import com.swimgym.app.data.model.Mappers.toEntity
import com.swimgym.app.data.api.VirtuagymApiClient
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.model.TrainingDto
import com.swimgym.app.data.parser.ScheduleParser
import com.swimgym.app.domain.repository.SwodLevel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingRepositoryRefreshTest {

    private val context = mockk<Context>(relaxUnitFun = true)
    private val api = mockk<VirtuagymApiClient>()
    private val dao = mockk<SwimGymDao>(relaxUnitFun = true)
    private val parser = mockk<ScheduleParser>()
    private val document = mockk<Document>()

    private fun repository() = TrainingRepositoryImpl(
        context = context,
        apiClient = api,
        dao = dao,
        parser = parser,
        calendarService = mockk(relaxUnitFun = true),
        notificationManager = mockk(relaxUnitFun = true)
    )

    private fun training(id: String) = TrainingDto(
        id = id,
        title = "Laps Swimming",
        instructor = "John",
        startTime = 1700000000L,
        endTime = 1700003600L
    )

    @Test
    fun `partial fetch does not delete cached classes from the failed week`() = runTest {
        coEvery { api.fetchHtml(any()) } returns document
        var calls = 0
        coEvery { parser.parseSchedule(any(), any(), any(), any()) } answers {
            calls++
            if (calls == 2) throw Exception("week failed") else listOf(training("week-$calls"))
        }
        coEvery { dao.getAllTrainingIds() } returns listOf("week-1", "week-2", "week-3")

        val result = repository().refreshSchedule()

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { dao.insertTrainings(any()) }
        coVerify(exactly = 0) { dao.deleteTrainingById(any()) }
    }

    @Test
    fun `partial fetch on a cold cache still returns the classes that were saved`() = runTest {
        coEvery { api.fetchHtml(any()) } returns document
        var fetches = 0
        coEvery { parser.parseSchedule(any(), any(), any(), any()) } answers {
            fetches++
            if (fetches == 2) throw Exception("week failed") else listOf(training("week-$fetches"))
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
        coEvery { api.fetchHtml(any()) } throws Exception("offline")
        coEvery { dao.getAllTrainingIds() } returns listOf("cached-1")

        val result = repository().refreshSchedule()

        assertFalse(result.isSuccess)
        coVerify(exactly = 0) { dao.insertTrainings(any()) }
        coVerify(exactly = 0) { dao.deleteTrainingById(any()) }
    }
}
