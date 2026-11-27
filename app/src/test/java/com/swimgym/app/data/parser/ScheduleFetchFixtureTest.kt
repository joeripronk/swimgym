package com.swimgym.app.data.parser

import com.swimgym.app.data.api.VirtuagymApiClient
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.repository.ScheduleFeeds
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Optional fixture test used by tools/fetch_schedule.sh. Set
 * SWIMGYM_SCHEDULE_FIXTURE_DIR to a directory of fetched week pages and this
 * test runs the real ScheduleParser against them. Without the variable it is
 * skipped so the normal unit-test suite stays self-contained.
 */
class ScheduleFetchFixtureTest {

    @Test
    fun `fetched schedule pages parse into SWOD and Laps classes`() = runTest {
        val dir = System.getenv("SWIMGYM_SCHEDULE_FIXTURE_DIR")
        assumeTrue("SWIMGYM_SCHEDULE_FIXTURE_DIR is not set", !dir.isNullOrBlank())

        val fixtures = File(dir)
            .listFiles { file -> file.name.startsWith("week-") && file.name.endsWith(".html") }
            ?.toList()
            .orEmpty()
        assumeTrue("No schedule fixtures found in $dir", fixtures.isNotEmpty())

        val dao = mockk<SwimGymDao>(relaxUnitFun = true)
        coEvery { dao.getTrainingById(any()) } returns null
        coEvery { dao.getInstructor(any()) } returns null
        val api = mockk<VirtuagymApiClient>()
        coEvery { api.fetchHtml(any()) } returns Jsoup.parse("<div></div>")
        val parser = ScheduleParserImpl(dao)

        fixtures.forEach { file ->
            val eventType = Regex("""-e(\d+)\.html$""")
                .find(file.name)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
            assumeTrue("Fixture name has no event type: ${file.name}", eventType != null)

            val parsed = parser.parseSchedule(
                doc = Jsoup.parse(file.readText()),
                weeksOffset = 1,
                now = 0L,
                apiClient = api,
                eventType = eventType!!
            )

            assertTrue("No classes parsed from ${file.name}", parsed.isNotEmpty())
            assertTrue("Wrong event type stamped in ${file.name}", parsed.all { it.eventType == eventType })
            if (eventType == ScheduleFeeds.LAPS) {
                assertTrue("Expected Laps classes in ${file.name}", parsed.any { it.title.contains("Laps", ignoreCase = true) })
            }
        }
    }
}
