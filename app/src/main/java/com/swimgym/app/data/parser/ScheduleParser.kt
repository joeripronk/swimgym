package com.swimgym.app.data.parser

import com.swimgym.app.data.local.SwimGymDao
import android.util.Log
import com.swimgym.app.data.local.entity.InstructorEntity
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.model.Mappers.toDto
import com.swimgym.app.data.model.TrainingDto
import com.swimgym.app.data.repository.ScheduleFeeds
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.nodes.Document

interface ScheduleParser {
    suspend fun parseSchedule(
        doc: org.jsoup.nodes.Document,
        weeksOffset: Int,
        now: Long,
        apiClient: com.swimgym.app.data.api.VirtuagymApiClient,
        eventType: Int = ScheduleFeeds.SWOD
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

    private data class ClassDetails(
        val className: String,
        val classId: String,
        val instructor: String,
        val startTime: Long,
        val endTime: Long,
        val isFull: Boolean,
        val isJoined: Boolean,
        val eventDate: String,
        val timeText: String,
        val trainingEntity: TrainingEntity,
        val cachedTraining: TrainingDto?,
        val imageUrl: String?,
        val detailsStale: Boolean
    )

    override suspend fun parseSchedule(
        doc: org.jsoup.nodes.Document,
        weeksOffset: Int,
        now: Long,
        apiClient: com.swimgym.app.data.api.VirtuagymApiClient,
        eventType: Int
    ): List<com.swimgym.app.data.model.TrainingDto> {
        val classes = mutableListOf<ClassDetails>()

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
                        eventType = eventType,
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

                    // Refresh detail fields (spots, description, cancel policy, ...)
                    // periodically too, not just when the instructor image is missing;
                    // otherwise a cached image leaves those fields stale indefinitely.
                    val detailsStale = cachedEntity == null ||
                        System.currentTimeMillis() - cachedEntity.lastFetched > 12 * 60 * 60 * 1000L
                    classes.add(
                        ClassDetails(
                            className = className,
                            classId = classId,
                            instructor = instructor,
                            startTime = startTime,
                            endTime = endTime,
                            isFull = isFull,
                            isJoined = isJoined,
                            eventDate = eventDate,
                            timeText = timeText,
                            trainingEntity = trainingEntity,
                            cachedTraining = cachedEntity?.toDto(),
                            imageUrl = dao.getInstructor(instructor)?.instructorImage,
                            detailsStale = detailsStale
                        )
                    )
                }
            }
        }

        // A week can contain dozens of stale classes. Fetch their details with
        // bounded concurrency; the semaphore keeps the portal from being flooded
        // while async still overlaps the network waits. Order is preserved.
        val refreshed = coroutineScope {
            val semaphore = Semaphore(DETAIL_FETCH_CONCURRENCY)
            classes.map { entry ->
                async {
                    if (!entry.detailsStale) {
                        entry
                    } else {
                        semaphore.withPermit {
                            try {
                                val details = parseTrainingDetails(entry.classId, entry.trainingEntity, apiClient)
                                val dto = details.toDto()
                                entry.copy(cachedTraining = dto, imageUrl = dto.imageUrl)
                            } catch (e: Exception) {
                                Log.w("ScheduleParser", "Failed to fetch details for ${entry.classId}: ${e.message}")
                                entry
                            }
                        }
                    }
                }
            }.map { it.await() }
        }

        val trainings = mutableListOf<TrainingDto>()
        refreshed.forEach { entry ->
            val imageUrl = entry.imageUrl ?: ""
            if (entry.cachedTraining != null) {
                trainings.add(
                    entry.cachedTraining.copy(
                        id = entry.classId,
                        eventType = eventType,
                        title = entry.className,
                        instructor = entry.instructor,
                        startTime = entry.startTime,
                        endTime = entry.endTime,
                        isFull = entry.isFull,
                        isJoined = entry.isJoined,
                        classTime = entry.timeText,
                        classDate = entry.eventDate,
                        imageUrl = imageUrl
                    )
                )
            } else {
                trainings.add(
                    TrainingDto(
                        id = entry.classId,
                        eventType = eventType,
                        title = entry.className,
                        instructor = entry.instructor,
                        startTime = entry.startTime,
                        endTime = entry.endTime,
                        isFull = entry.isFull,
                        isJoined = entry.isJoined,
                        classTime = entry.timeText,
                        classDate = entry.eventDate,
                        imageUrl = imageUrl
                    )
                )
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
        val result = TrainingDetailsParser.parse(doc, trainingId, baseTraining)
        Log.d("ScheduleParser", "Parsed training details: id=$trainingId, title=${result.title}, instructor=${result.instructor}, location=${result.location}, spots=${result.spotsAvailable}/${result.totalSpots}, joined=${result.isJoined}")

        if (result.imageUrl.isNotEmpty()) {
            dao.insertInstructor(
                InstructorEntity(
                    instructorName = result.instructor,
                    instructorLink = result.instructorLink,
                    instructorImage = result.imageUrl
                )
            )
        }

        return result
    }

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
            Log.w("ScheduleParser", "Failed to parse class time; using fallback", e)
        }
        return Pair(0, 3600)
    }

    companion object {
        private const val DETAIL_FETCH_CONCURRENCY = 4
        private val GYM_ZONE_ID: ZoneId = ZoneId.of("Europe/Amsterdam")
        private val GYM_TIME_ZONE: java.util.TimeZone =
            java.util.TimeZone.getTimeZone("Europe/Amsterdam")
    }
}
