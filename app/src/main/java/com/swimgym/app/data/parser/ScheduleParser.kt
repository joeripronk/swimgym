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
import java.util.TimeZone

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
        Log.d("ScheduleParser", "Found ${days.size} day columns in week $weeksOffset")

        days.forEach dayColumn@{ dayColumn ->
            val dayHeader = dayColumn.selectFirst(".day_head")
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
                val isFull = classElement.selectFirst(".full")?.text() == "VOL"
                val isJoined = classElement.selectFirst("div.joined") != null
                val eventDate = extractDateFromClass(classElement) ?: dayName
                val (startTime, endTime) = parseTimes(timeText, eventDate)

                if (classId.isNotBlank() && startTime > now) {
                    val trainingEntity = TrainingEntity(
                        id = classId,
                        title = className,
                        instructor = instructor,
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
                        classDate = eventDate
                    )

                    var cachedTraining = dao.getTrainingById(classId)?.toDto()
                    var imageUrl = dao.getInstructor(instructor)?.instructorImage
                    if (imageUrl == null) {
                        val detailsResult = parseTrainingDetails(classId, trainingEntity, apiClient)
                        cachedTraining = detailsResult.toDto()
                        imageUrl = cachedTraining?.imageUrl
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

        val instructorLink =
            doc.selectFirst(".event-details-icon a[href^=/userid]")?.attr("href") ?: ""
        val instructor =
            doc.selectFirst(".event-details-icon a[href^=/userid]")?.text() ?: "TBA"
        val location =
            doc.selectFirst("div.event-details-icons:nth-child(3) > div.event-details-icon:nth-child(2) > div.icon-text:nth-child(2)")
                ?.text() ?: "SwimGym"
        val spotsText =
            doc.selectFirst("div.event-details-icons:nth-child(2) > div.event-details-icon:nth-child(3) > div.icon-text:nth-child(2)")
                ?.text() ?: "invalid"
        val imageUrl = doc.selectFirst(".event-image-holder img")?.attr("src") ?: ""
        val description = doc.selectFirst(".event-description-holder")?.text() ?: ""
        val cost = doc.selectFirst(".event-details-icon.ticket-star .icon-text")?.text() ?: ""
        val isJoined = doc.selectFirst(".booking-text.green") != null
        val cancelPolicy =
            doc.selectFirst(".event-actions > div:not(.booking-text)")?.text() ?: ""

        val spotsParts = spotsText.split("/").map { it.trim() }
        val spotsTaken = spotsParts.getOrNull(0)?.toIntOrNull() ?: 0
        var totalSpots = spotsParts.getOrNull(1)?.toIntOrNull() ?: 0

        val spotsAvailable = totalSpots - spotsTaken
        val isFull = spotsAvailable == 0

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
            cancelPolicy = cancelPolicy
        )
    }

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
            val parts = timeText.trim().split(Regex("-"))
            if (parts.size >= 2) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val startStr = parts[0].trim()
                val endStr = parts[1].trim()

                val startDate = dateFormat.parse(dateStr)
                val startTime = timeFormat.parse(startStr)
                val endTime = timeFormat.parse(endStr)

                if (startDate != null && startTime != null && endTime != null) {
                    val calendar = Calendar.getInstance()
                    calendar.time = startDate

                    calendar.set(Calendar.HOUR_OF_DAY, startTimeHours(startTime))
                    calendar.set(Calendar.MINUTE, startTimeMinutes(startTime))
                    val start = calendar.timeInMillis / 1000

                    calendar.set(Calendar.HOUR_OF_DAY, startTimeHours(endTime))
                    calendar.set(Calendar.MINUTE, startTimeMinutes(endTime))
                    val end = calendar.timeInMillis / 1000

                    return Pair(start, end)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(0, 3600)
    }

    private fun startTimeHours(time: java.util.Date): Int {
        val cal = Calendar.getInstance()
        cal.time = time
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    private fun startTimeMinutes(time: java.util.Date): Int {
        val cal = Calendar.getInstance()
        cal.time = time
        return cal.get(Calendar.MINUTE)
    }
}
