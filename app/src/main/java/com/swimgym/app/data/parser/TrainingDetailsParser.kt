package com.swimgym.app.data.parser

import com.swimgym.app.data.local.entity.TrainingEntity
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Shared, side-effect-free parser for a Virtuagym class-detail page.
 *
 * Detail fields are resolved by the semantic `.event-details-icon` tooltip/icon
 * first (with the legacy positional selectors as fallbacks), so inserting an
 * icon no longer shifts every positional selector. Fetching, logging and all
 * database writes stay with the callers.
 */
internal object TrainingDetailsParser {
    fun parse(doc: Document, trainingId: String, baseTraining: TrainingEntity): TrainingEntity {
        val title = doc.selectFirst(".modal-title-replacement, .class-info .class-name")?.text() ?: ""
        if (title.isEmpty()) {
            throw Exception("failed to fetch data")
        }

        val instructorIcon = findDetailIcon(doc, "Instructeur", "staff")
        val instructorLink = instructorIcon?.selectFirst("a[href^=/userid]")?.attr("href")
            ?: doc.selectFirst(".event-details-icon a[href^=/userid]")?.attr("href")
            ?: ""
        val instructor = detailValue(doc, "Instructeur", "staff")
            ?: instructorIcon?.selectFirst("a[href^=/userid]")?.text()
            ?: doc.selectFirst(".event-details-icon a[href^=/userid]")?.text()
            ?: "TBA"
        val location = detailValue(doc, "Locatie", "location")
            ?: doc.selectFirst("div.event-details-icons:nth-child(3) > div.event-details-icon:nth-child(2) > div.icon-text:nth-child(2)")
                ?.text()
            ?: "SwimGym"
        val spotsText = detailValue(doc, "Bezet", "group")
            ?: doc.selectFirst("div.event-details-icons:nth-child(2) > div.event-details-icon:nth-child(3) > div.icon-text:nth-child(2)")
                ?.text()
        val imageUrl = doc.selectFirst(".event-image-holder img")?.attr("src") ?: ""
        val description = doc.selectFirst(".event-description-holder")?.text() ?: ""
        val cost = detailValue(doc, "Kosten", "ticket-star")
            ?: doc.selectFirst(".event-details-icon:has(.ticket-star) .icon-text")?.text()
            ?: ""
        val isJoined = doc.selectFirst(".booking-text.green") != null
        val cancelPolicy = doc.selectFirst(".event-actions > div:not(.booking-text)")?.text() ?: ""

        val spotsParts = spotsText?.split("/")?.map { it.trim() }
        val spotsTaken = spotsParts?.getOrNull(0)?.toIntOrNull()
        val totalSpotsParsed = spotsParts?.getOrNull(1)?.toIntOrNull()
        val spotsKnown = spotsTaken != null && totalSpotsParsed != null
        val totalSpots = if (spotsKnown) (totalSpotsParsed ?: 0) else 0
        // Unknown spots must not be reported as "full": a markup change would
        // otherwise hide every class and trigger needless retry failures.
        val spotsAvailable = if (spotsKnown) maxOf(0, totalSpots - (spotsTaken ?: 0)) else 0
        val explicitFull = doc.selectFirst(".full")?.text()?.trim()?.let {
            it.equals("VOL", ignoreCase = true) || it.equals("FULL", ignoreCase = true)
        } == true
        val isFull = !isJoined && (explicitFull || (spotsKnown && spotsAvailable == 0))

        return TrainingEntity(
            id = trainingId,
            instructor = instructor,
            instructorLink = instructorLink.ifEmpty { baseTraining.instructorLink },
            startTime = baseTraining.startTime,
            endTime = baseTraining.endTime,
            classDate = baseTraining.classDate,
            classTime = baseTraining.classTime,
            title = title,
            location = location,
            spotsAvailable = spotsAvailable,
            totalSpots = totalSpots,
            description = description,
            isFull = isFull,
            cost = cost,
            isJoined = isJoined,
            imageUrl = imageUrl,
            cancelPolicy = cancelPolicy,
            eventId = baseTraining.eventId,
            calendarId = baseTraining.calendarId
        )
    }

    /** Finds a detail icon by tooltip title (Dutch/English) or icon class. */
    private fun findDetailIcon(doc: Document, vararg keys: String): Element? {
        val icons = doc.select(".event-details-icon")
        return keys.firstNotNullOfOrNull { key ->
            icons.firstOrNull { icon ->
                icon.selectFirst(".ui-tooltip-icon-overlay[title]")?.attr("title")
                    ?.equals(key, ignoreCase = true) == true ||
                    icon.selectFirst(".ui-tooltip-icon")?.hasClass(key) == true
            }
        }
    }

    /** Reads the text value of the first matching detail icon. */
    private fun detailValue(doc: Document, vararg keys: String): String? =
        findDetailIcon(doc, *keys)
            ?.selectFirst(".icon-text")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
}
