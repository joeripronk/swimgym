package com.swimgym.app.data.parser

import com.swimgym.app.data.api.VirtuagymApiClient
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.TrainingEntity
import io.mockk.coEvery
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
}
