package com.swimgym.app.data.parser

import com.swimgym.app.data.local.SwimGymDao
import android.util.Log
import com.swimgym.app.data.local.entity.InstructorEntity
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.model.Mappers.toDto
import com.swimgym.app.data.model.TrainingDto
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Locale
import org.jsoup.nodes.Document

interface ScheduleParser {
    suspend fun parseSchedule(
        doc: org.jsoup.nodes.Document,
        weeksOffset: Int,
        now: Long,
        apiClient: com.swimgym.app.data.api.VirtuagymApiClient
    ): List<com.swimgym.app.data.model.TrainingDto>

    suspend fun parseTrainingDetails(
        trainingId: String,
        baseTraining: TrainingEntity,
        apiClient: com.swimgym.app.data.api.VirtuagymApiClient
    ): TrainingEntity
}

class ScheduleParserImpl(
    private val dao: SwimGymDao,
    private val baseUrl: String = "https://swimgym.virtuagym.com"
) : ScheduleParser {

    override suspend fun parseSchedule(
        doc: org.jsoup.nodes.Document,
        weeksOffset: Int,
        now: Long,
        apiClient: com.swimgym.app.data.api.VirtuagymApiClient
    ): List<com.swimgym.app.data.model.TrainingDto> {
        val trainings = mutableListOf<TrainingDto>()

        val days = doc.select("#schedule_content .cal_column")
        // Day headers live in the sibling #schedule_header, not inside the
        // #schedule_content columns. Pair them by index so the date fallback
        // in resolveEventDate() is actually reachable.
        val dayHeaders = doc.select("#schedule_header .day_head")
        Log.d("ScheduleParser", "Found ${days.size} day columns in week $weeksOffset")

        days.forEachIndexed { index, dayColumn ->
            val dayHeader = dayHeaders.getOrNull(index)
            val dayName = dayHeader?.selectFirst(".day_name_long")?.text()
                ?: dayHeader?.selectFirst(".day_name_short")?.text()
                ?: ""

                    dayColumn.select(".class").forEach classElement@{ classElement ->
                        val className =
                            classElement.selectFirst(".classname")?.text() ?: return@classElement
                        if (className.isBlank()) return@classElement

                        val classId = classElement.id()
                        Log.d("ScheduleParser", "Parsing class: $classId - $className")
                val timeText = classElement.selectFirst(".time")?.text() ?: ""
                val instructor = classElement.selectFirst(".instructor i")?.text() ?: "TBA"
                val isFull = classElement.selectFirst(".full")?.text()?.trim()?.let {
                    it.equals("VOL", ignoreCase = true) || it.equals("FULL", ignoreCase = true)
                } == true
                val isJoined = classElement.selectFirst("div.joined") != null
                val eventDate = resolveEventDate(extractDateFromClass(classElement), dayName, weeksOffset)
                val (startTime, endTime) = parseTimes(timeText, eventDate)

                if (classId.isNotBlank() && startTime > now) {
                    val cachedEntity = dao.getTrainingById(classId)
                    // parseTrainingDetails() builds a new entity from this base, so
                    // carry over the calendar linkage (and instructor link). Otherwise
                    // a periodic detail refresh writes blank values back and the class
                    // is reported as no longer being in the calendar.
                    val trainingEntity = TrainingEntity(
                        id = classId,
                        title = className,
                        instructor = instructor,
                        instructorLink = cachedEntity?.instructorLink ?: "",
                        startTime = startTime,
                        endTime = endTime,
                        location = "",
                        isFull = isFull,
                        isJoined = isJoined,
                        spotsAvailable = 0,
                        classTime = timeText,
                        imageUrl = "",
                        description = "",
                        cost = "",
                        cancelPolicy = "",
                        classDate = eventDate,
                        eventId = cachedEntity?.eventId ?: "",
                        calendarId = cachedEntity?.calendarId ?: "",
                        lastFetched = cachedEntity?.lastFetched ?: System.currentTimeMillis()
                    )

                    var cachedTraining = cachedEntity?.toDto()
                    var imageUrl = dao.getInstructor(instructor)?.instructorImage
                    // Refresh detail fields (spots, description, cancel policy, ...)
                    // periodically too, not just when the instructor image is missing;
                    // otherwise a cached image leaves those fields stale indefinitely.
                    val detailsStale = cachedEntity == null ||
                        System.currentTimeMillis() - cachedEntity.lastFetched > 12 * 60 * 60 * 1000L
                    if (detailsStale) {
                        try {
                            val detailsResult = parseTrainingDetails(classId, trainingEntity, apiClient)
                            cachedTraining = detailsResult.toDto()
                            imageUrl = cachedTraining?.imageUrl
                        } catch (e: Exception) {
                            android.util.Log.w("ScheduleParser", "Failed to fetch details for $classId: ${e.message}")
                        }
                    }
                    if (imageUrl == null) {
                        imageUrl = ""
                    }

                    if (cachedTraining != null) {
                        trainings.add(
                            cachedTraining.copy(
                                id = classId,
                                title = className,
                                instructor = instructor,
                                startTime = startTime,
                                endTime = endTime,
                                isFull = isFull,
                                isJoined = isJoined,
                                classTime = timeText,
                                classDate = eventDate,
                                imageUrl = imageUrl
                            )
                        )
                    } else {
                        trainings.add(
                            TrainingDto(
                                id = classId,
                                title = className,
                                instructor = instructor,
                                startTime = startTime,
                                endTime = endTime,
                                isFull = isFull,
                                isJoined = isJoined,
                                classTime = timeText,
                                classDate = eventDate,
                                imageUrl = imageUrl
                            )
                        )
                    }
                }
            }
        }

        return trainings
    }

    override suspend fun parseTrainingDetails(
        trainingId: String,
        baseTraining: TrainingEntity,
        apiClient: com.swimgym.app.data.api.VirtuagymApiClient
    ): TrainingEntity {
        val url = "$baseUrl/classes/class/$trainingId?embedded=0"
        Log.d("ScheduleParser", "Fetching training details from URL: $url")
        val doc = apiClient.fetchHtml(url)

        val title =
            doc.selectFirst(".modal-title-replacement, .class-info .class-name")?.text() ?: ""
        if (title.isNullOrEmpty()) {
            Log.e("ScheduleParser", "Failed to parse title for trainingId: $trainingId")
            throw Exception("failed to fetch data")
        }

        // The portal renders each detail as an .event-details-icon with a
        // semantic tooltip title ("Datum", "Bezet", "Instructeur", ...) and an
        // icon class. Prefer those over nth-child chains: inserting a single
        // icon shifts every positional selector and silently corrupts the data.
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
        val cancelPolicy =
            doc.selectFirst(".event-actions > div:not(.booking-text)")?.text() ?: ""

        val spotsParts = spotsText?.split("/")?.map { it.trim() }
        val spotsTaken = spotsParts?.getOrNull(0)?.toIntOrNull()
        val totalSpotsParsed = spotsParts?.getOrNull(1)?.toIntOrNull()
        val spotsKnown = spotsTaken != null && totalSpotsParsed != null
        val totalSpots = if (spotsKnown) totalSpotsParsed!! else 0
        // Unknown spots must not be reported as "full": a markup change would
        // otherwise hide every class and trigger needless retry failures.
        val spotsAvailable = if (spotsKnown) maxOf(0, totalSpots - spotsTaken!!) else 0
        val explicitFull = doc.selectFirst(".full")?.text()?.trim()?.let {
            it.equals("VOL", ignoreCase = true) || it.equals("FULL", ignoreCase = true)
        } == true
        val isFull = !isJoined && (explicitFull || (spotsKnown && spotsAvailable == 0))

        Log.d("ScheduleParser", "Parsed training details: id=$trainingId, title=$title, instructor=$instructor, location=$location, spots=$spotsAvailable/$totalSpots, joined=$isJoined")

        if (imageUrl.isNotEmpty()) {
            dao.insertInstructor(
                InstructorEntity(
                    instructorName = instructor,
                    instructorLink = instructorLink,
                    instructorImage = imageUrl
                )
            )
        }

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
    private fun findDetailIcon(doc: Document, vararg keys: String): org.jsoup.nodes.Element? {
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

    /**
     * Falls back to the "d MMM" portion of the day header when Virtuagym omits
     * the internal-event-day token, so classes are not silently dropped because
     * SimpleDateFormat cannot parse a weekday label such as "maandag 27 apr".
     */
    private fun resolveEventDate(token: String?, dayName: String, weeksOffset: Int): String {
        if (!token.isNullOrBlank()) return token
        val match = Regex("""(\d{1,2})\s+([^\s]+)""").find(dayName) ?: return dayName
        val day = match.groupValues[1].toIntOrNull() ?: return dayName
        val month = monthNames[match.groupValues[2].lowercase(Locale.ROOT)] ?: return dayName
        // weeksOffset: 0 = last week, 1 = this week, 2 = next week.
        val anchor = LocalDate.now(GYM_ZONE_ID)
            .with(DayOfWeek.MONDAY)
            .plusWeeks((weeksOffset - 1).toLong())
        val resolved = listOf(anchor.year - 1, anchor.year, anchor.year + 1)
            .mapNotNull { year ->
                try {
                    LocalDate.of(year, month, day)
                } catch (e: Exception) {
                    null
                }
            }
            .minByOrNull { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(anchor, it)) }
            ?: return dayName
        return "%02d-%02d-%04d".format(resolved.dayOfMonth, resolved.monthValue, resolved.year)
    }

    private val monthNames = mapOf(
        "jan" to 1, "januari" to 1,
        "feb" to 2, "februari" to 2,
        "mrt" to 3, "maart" to 3,
        "apr" to 4, "april" to 4,
        "mei" to 5, "may" to 5,
        "jun" to 6, "juni" to 6,
        "jul" to 7, "juli" to 7,
        "aug" to 8, "augustus" to 8,
        "sep" to 9, "sept" to 9, "september" to 9,
        "okt" to 10, "oktober" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    private fun extractDateFromClass(classElement: org.jsoup.nodes.Element): String? {
        val classAttr = classElement.className()
        val match = Regex("""internal-event-day-(\d{2})-(\d{2})-(\d{4})""").find(classAttr)
        return match?.let {
            val (day, month, year) = it.destructured
            "$day-$month-$year"
        }
    }

    private fun parseTimes(timeText: String, dateStr: String): Pair<Long, Long> {
        try {
            val parts = timeText.trim().split(Regex("[-–—]"))
            if (parts.size >= 2) {
                val startParts = parts[0].trim().split(":")
                val endParts = parts[1].trim().split(":")
                val startHour = startParts.getOrNull(0)?.toIntOrNull() ?: return Pair(0, 3600)
                val startMinute = startParts.getOrNull(1)?.toIntOrNull() ?: 0
                val endHour = endParts.getOrNull(0)?.toIntOrNull() ?: return Pair(0, 3600)
                val endMinute = endParts.getOrNull(1)?.toIntOrNull() ?: 0

                // The schedule is published in the gym's local time, not the
                // device's. Parsing in the device timezone shifted every class
                // for users travelling outside the Netherlands.
                val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
                    timeZone = GYM_TIME_ZONE
                }
                val startDate = dateFormat.parse(dateStr) ?: return Pair(0, 3600)

                val calendar = Calendar.getInstance(GYM_TIME_ZONE).apply { time = startDate }
                calendar.set(Calendar.HOUR_OF_DAY, startHour)
                calendar.set(Calendar.MINUTE, startMinute)
                val start = calendar.timeInMillis / 1000

                calendar.set(Calendar.HOUR_OF_DAY, endHour)
                calendar.set(Calendar.MINUTE, endMinute)
                var end = calendar.timeInMillis / 1000
                // A class that runs past midnight (e.g. 23:30 - 00:30) would
                // otherwise get an end time before its start time.
                if (end <= start) {
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                    end = calendar.timeInMillis / 1000
                }

                return Pair(start, end)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(0, 3600)
    }

    companion object {
        private val GYM_ZONE_ID: ZoneId = ZoneId.of("Europe/Amsterdam")
        private val GYM_TIME_ZONE: java.util.TimeZone =
            java.util.TimeZone.getTimeZone("Europe/Amsterdam")
    }
}
