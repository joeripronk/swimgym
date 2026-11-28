package com.swimgym.app.data.parser

import com.swimgym.app.data.api.VirtuagymApiClient
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.TrainingEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScheduleParserDetailsTest {

    private val dao = mockk<SwimGymDao>(relaxUnitFun = true)
    private val api = mockk<VirtuagymApiClient>()

    private val baseTraining = TrainingEntity(
        id = "123",
        title = "SWOD",
        instructor = "TBA",
        startTime = 1700000000L,
        endTime = 1700003600L,
        location = "",
        spotsAvailable = 0,
        classTime = "19:00 - 20:00",
        classDate = "05-05-2026",
        imageUrl = "",
        description = "",
        cost = "",
        cancelPolicy = ""
    )

    private fun detailHtml(iconOrder: List<String>) = buildString {
        append("<div class=\"modal-title-replacement hide\">SWOD pro level</div>")
        append("<div class=\"event-image-holder\"><img src=\"/img/class.jpg\"></div>")
        append("<div class=\"event-description-holder\">Swim of the day</div>")
        append("<div class=\"event-details-icons\">")
        iconOrder.forEach { key ->
            val tooltip = when (key) {
                "staff" -> "Instructeur"
                "location" -> "Locatie"
                "group" -> "Bezet"
                else -> "Kosten"
            }
            val value = when (key) {
                "staff" -> "<a href=\"/userid/18246773\">Kaylee de Jong</a>"
                "location" -> "Voorzijde"
                "group" -> "11 / 12"
                else -> "1 SwimGym Credits"
            }
            append("<div class=\"event-details-icon\">")
            append("<div class=\"ui-tooltip-icon size-24 $key\">")
            append("<div class=\"ui-tooltip-icon-overlay\" title=\"$tooltip\"></div></div>")
            append("<div class=\"icon-text\">$value</div></div>")
        }
        append("</div>")
        append("<div class=\"event-actions\">")
        append("<div class=\"booking-text green\">Je hebt dit gereserveerd</div>")
        append("<div>Je kan kosteloos annuleren tot 12 uur</div></div>")
    }

    @Test
    fun `detail fields are found by semantic icon regardless of order`() = runTest {
        val html = detailHtml(listOf("group", "ticket-star", "staff", "location"))
        coEvery { api.fetchHtml(any()) } returns Jsoup.parse(html, "https://swimgym.virtuagym.com/classes/class/123?embedded=0")

        val result = ScheduleParserImpl(dao).parseTrainingDetails("123", baseTraining, api)

        assertEquals("SWOD pro level", result.title)
        assertEquals("Kaylee de Jong", result.instructor)
        assertEquals("/userid/18246773", result.instructorLink)
        assertEquals("Voorzijde", result.location)
        assertEquals(1, result.spotsAvailable)
        assertEquals(12, result.totalSpots)
        assertEquals("1 SwimGym Credits", result.cost)
        assertTrue(result.isJoined)
        assertFalse(result.isFull)
    }

    @Test
    fun `parses the saved booking info page`() = runTest {
        val page = generateSequence(File(".").absoluteFile) { it.parentFile }
            .take(5)
            .map { File(it, "tools/book_info.html") }
            .firstOrNull { it.isFile }
            ?: return@runTest

        coEvery { api.fetchHtml(any()) } returns Jsoup.parse(
            page.readText(),
            "https://swimgym.virtuagym.com/classes/class/123?embedded=0"
        )

        val result = ScheduleParserImpl(dao).parseTrainingDetails("123", baseTraining, api)

        assertEquals("Kaylee de Jong", result.instructor)
        assertEquals("/userid/18246773", result.instructorLink)
        assertEquals("Voorzijde", result.location)
        assertEquals(1, result.spotsAvailable)
        assertEquals(12, result.totalSpots)
        assertEquals("1 SwimGym Credits", result.cost)
        assertTrue(result.isJoined)
    }

    @Test
    fun `parseSchedule stamps the laps feed on each class`() = runTest {
        val html = """
            <div id="schedule_header"><div class="day_head"><span class="day_name_long">zaterdag 28 november</span></div></div>
            <div id="schedule_content"><div class="cal_column">
              <div id="laps-1" class="class internal-event-day-28-11-2026" onclick="openScheduleModal('/classes/class/laps-1?embedded=0')">
                <div class="c_holder">
                  <span class="classname">Laps</span>
                  <span class="time">07:00 - 08:00</span>
                  <span class="instructor"><i>John</i></span>
                </div>
              </div>
            </div></div>
        """.trimIndent()
        coEvery { dao.getTrainingById("laps-1") } returns null
        coEvery { dao.getInstructor(any()) } returns null
        coEvery { api.fetchHtml(any()) } returns Jsoup.parse("<div></div>")

        val parsed = ScheduleParserImpl(dao).parseSchedule(
            doc = Jsoup.parse(html),
            weeksOffset = 1,
            now = 0L,
            apiClient = api,
            eventType = com.swimgym.app.data.repository.ScheduleFeeds.LAPS
        )

        assertEquals(1, parsed.size)
        assertEquals(com.swimgym.app.data.repository.ScheduleFeeds.LAPS, parsed.first().eventType)
        assertEquals("Laps", parsed.first().title)
        // Schedule parsing must not fetch class detail pages; those are loaded
        // on demand when a class is opened, and caused minute-long refreshes.
        coVerify(exactly = 0) { api.fetchHtml(any()) }
        coVerify(exactly = 0) { api.fetchHtmlBatch(any()) }
    }
}
