package com.swimgym.app.data.api

import com.swimgym.app.data.model.BookingResponse
import com.swimgym.app.data.model.TrainingDto
import com.swimgym.app.data.model.UserDto
import com.swimgym.app.domain.model.Training
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebScraper @Inject constructor() {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val baseUrl = "https://swimgym.virtuagym.com"
    private var cookies: Map<String, String> = emptyMap()

    fun setCookies(cookieMap: Map<String, String>) {
        cookies = cookieMap
    }

    private fun buildRequest(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        for ((name, value) in cookies) {
            builder.addHeader("Cookie", "$name=$value")
        }
        return builder
    }

    suspend fun login(email: String, password: String): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            val csrfResponse = client.newCall(
                Request.Builder().url("$baseUrl/login").build()
            ).execute()

            val doc = Jsoup.parse(csrfResponse.body?.string() ?: "")
            val csrfToken = doc.selectFirst("input[name=_csrf]")?.attr("value") 
                ?: doc.selectFirst("#global_csrf_token")?.attr("value")
                ?: ""

            val formBody = FormBody.Builder()
                .add("_csrf", csrfToken)
                .add("email", email)
                .add("password", password)
                .build()

            val loginResponse = client.newCall(
                Request.Builder()
                    .url("$baseUrl/login")
                    .post(formBody)
                    .build()
            ).execute()

            cookies = loginResponse.headers.toMultimap()
                .filter { it.key.lowercase().startsWith("set-cookie") }
                .map { parseCookie(it.value.firstOrNull() ?: "") }
                .toMap()

            if (cookies.containsKey("virtuagym_u") || cookies.values.any { it.contains("virtuagym_u") }) {
                val userDoc = Jsoup.connect(baseUrl)
                    .cookies(cookies)
                    .get()

                val userName = userDoc.selectFirst(".user-menu-name")?.text()
                    ?: userDoc.selectFirst("[data-cy=topNavBarUserMenuItemUserProfileName]")?.text()
                    ?: "User"

                Result.success(UserDto(id = 1, name = userName, email = email))
            } else {
                Result.failure(Exception("Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    enum class SwodLevel {
        ALL, STARTERS, MID, PRO
    }

    suspend fun getSchedule(
        startDate: String? = null,
        level: SwodLevel = SwodLevel.ALL,
        hideFullyBooked: Boolean = true
    ): Result<List<TrainingDto>> = withContext(Dispatchers.IO) {
        try {
            val activityId = when (level) {
                SwodLevel.STARTERS -> "activity_id=59161"
                SwodLevel.MID -> "activity_id=62501"
                SwodLevel.PRO -> "activity_id=68621"
                SwodLevel.ALL -> ""
            }
            
            val base = if (startDate != null) {
                "$baseUrl/classes/week/$startDate?event_type=8"
            } else {
                "$baseUrl/classes?event_type=8"
            }
            val url = if (activityId.isNotEmpty()) "$base&$activityId" else base

            val doc = Jsoup.connect(url)
                .cookies(cookies)
                .userAgent("Mozilla/5.0")
                .get()

            val trainings = mutableListOf<TrainingDto>()
            
            doc.select("#schedule_content .cal_column").forEach dayColumn@{ dayColumn ->
                val dayHeader = dayColumn.selectFirst(".day_head")
                val dayName = dayHeader?.selectFirst(".day_name_long")?.text()
                    ?: dayHeader?.selectFirst(".day_name_short")?.text()
                    ?: ""

                dayColumn.select(".class").forEach classElement@{ classElement ->
                    val className = classElement.selectFirst(".classname")?.text() ?: return@classElement
                    if (className.isBlank()) return@classElement

                    val classId = classElement.id()
                    val timeText = classElement.selectFirst(".time")?.text() ?: ""
                    val instructor = classElement.selectFirst(".instructor i")?.text() ?: "TBA"
                    val isFull = classElement.selectFirst(".full") != null
                    val isJoined = classElement.selectFirst("div.joined") != null

                    if (hideFullyBooked && isFull && !isJoined) return@classElement

                    val eventDate = extractDateFromClass(classElement) ?: dayName
                    val (startTime, endTime) = parseTimes(timeText, eventDate)
                    val spotsAvailable = if (isFull && !isJoined) 0 else 10

                    if (classId.isNotBlank()) {
                        trainings.add(
                            TrainingDto(
                                id = classId,
                                title = className,
                                instructor = instructor,
                                startTime = startTime,
                                endTime = endTime,
                                location = "SwimGym",
                                spotsAvailable = spotsAvailable,
                                isJoined = isJoined,
                                classTime = timeText,
                                classDate = eventDate
                            )
                        )
                    }
                }
            }

            Result.success(trainings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun bookTraining(
        trainingId: String,
        className: String = "",
        classTime: String = "",
        classDate: String = ""
    ): Result<BookingResponse> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("action", "reserve_class")
                .add("absent_reason", "")
                .add("participant_name", "")
                .add("participant_email", "")
                .add("participant_notes", "")
                .add("participant_id", "")
                .add("present", "")
                .add("participant_member_id", "")
                .add("book_recurring", "")
                .add("additional_note", "")
                .add("class_name", className)
                .add("class_time", classTime)
                .add("class_date", classDate)
                .add("send_email", "1")
                .add("instance_of", "")
                .add("cancel_recurring", "")
                .add("email_participants_subject", "")
                .add("email_participants_content", "")
                .add("waiting_member_id", "")
                .add("activity_id_filter", "")
                .add("club_coach_id_filter", "")
                .add("attendees", "0")
                .build()

            val response = client.newCall(
                buildRequest("$baseUrl/classes/class/$trainingId?event_type=8")
                    .post(formBody)
                    .build()
            ).execute()

            if (response.isSuccessful || response.code == 302) {
                Result.success(
                    BookingResponse(
                        id = trainingId.hashCode(),
                        trainingId = trainingId,
                        userId = 1,
                        status = "confirmed",
                        className = className,
                        classTime = classTime,
                        classDate = classDate
                    )
                )
            } else {
                Result.failure(Exception("Booking failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelBooking(
        trainingId: String,
        className: String = "",
        classTime: String = "",
        classDate: String = ""
    ): Result<BookingResponse> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("action", "cancel_reserve_class")
                .add("absent_reason", "unknown")
                .add("participant_name", "")
                .add("participant_email", "")
                .add("participant_notes", "")
                .add("participant_id", "")
                .add("present", "")
                .add("participant_member_id", "")
                .add("book_recurring", "")
                .add("additional_note", "")
                .add("class_name", className)
                .add("class_time", classTime)
                .add("class_date", classDate)
                .add("send_email", "1")
                .add("instance_of", "")
                .add("cancel_recurring", "")
                .add("email_participants_subject", "")
                .add("email_participants_content", "")
                .add("waiting_member_id", "")
                .add("activity_id_filter", "")
                .add("club_coach_id_filter", "")
                .add("attendees", "0")
                .build()

            val response = client.newCall(
                buildRequest("$baseUrl/classes/class/$trainingId?event_type=8")
                    .post(formBody)
                    .build()
            ).execute()

            if (response.isSuccessful || response.code == 302) {
                Result.success(
                    BookingResponse(
                        id = trainingId.hashCode(),
                        trainingId = trainingId,
                        userId = 1,
                        status = "cancelled"
                    )
                )
            } else {
                Result.failure(Exception("Cancel failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBookings(): Result<List<BookingResponse>> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect("$baseUrl/bookings")
                .cookies(cookies)
                .get()

            val bookings = mutableListOf<BookingResponse>()
            doc.select(".booking, .booking-item, .my-booking, tr.booking-row").forEach { element ->
                val id = element.attr("data-id").toIntOrNull() ?: 0
                var trainingId: String = element.attr("data-training-id")
                if (trainingId.isBlank()) {
                    val href = element.selectFirst("a[href*=training]")?.attr("href") ?: ""
                    trainingId = href.substringAfter("/training/").substringBefore("/")
                }
                val status = element.selectFirst(".status, .booking-status")?.text()?.lowercase() ?: "confirmed"

                if (trainingId.isNotBlank()) {
                    bookings.add(
                        BookingResponse(
                            id = id,
                            trainingId = trainingId,
                            userId = 1,
                            status = status
                        )
                    )
                }
            }

            Result.success(bookings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class TrainingDetails(
        val id: String,
        val title: String,
        val instructor: String,
        val instructorLink: String,
        val location: String,
        val spotsAvailable: Int,
        val totalSpots: Int,
        val imageUrl: String,
        val description: String,
        val cost: String,
        val isJoined: Boolean,
        val cancelPolicy: String
    )

    suspend fun getTrainingDetails(trainingId: String): Result<TrainingDetails> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect("$baseUrl/classes/class/$trainingId?embedded=0")
                .cookies(cookies)
                .userAgent("Mozilla/5.0")
                .get()
            val title = doc.selectFirst(".modal-title-replacement, .class-info .class-name")?.text() ?: ""
            val instructorLink = doc.selectFirst(".event-details-icon a[href^=/userid]")?.attr("href") ?: ""
            val instructor = doc.selectFirst(".event-details-icon a[href^=/userid]")?.text() ?: "TBA"
            val location = doc.selectFirst("div.event-details-icons:nth-child(3) > div.event-details-icon:nth-child(2) > div.icon-text:nth-child(2)")?.text() ?: "SwimGym"
            //val timeText = doc.selectFirst(".event-details-icon.clock .icon-text")?.text() ?: ""
            //val dateText = doc.selectFirst(".event-details-icon.calendar .icon-text")?.text() ?: ""
            val spotsText = doc.selectFirst("div.event-details-icons:nth-child(2) > div.event-details-icon:nth-child(3) > div.icon-text:nth-child(2)")?.text() ?: "invalid"
            val imageUrl = doc.selectFirst(".event-image-holder img")?.attr("src") ?: ""
            val description = doc.selectFirst(".event-description-holder")?.text() ?: ""
            val cost = doc.selectFirst(".event-details-icon.ticket-star .icon-text")?.text() ?: ""
            val isJoined = doc.selectFirst(".booking-text.green") != null
            val cancelPolicy = doc.selectFirst(".event-actions > div:not(.booking-text)")?.text() ?: ""

            val spotsParts = spotsText.split("/").map { it.trim() }
            val spotsTaken = spotsParts.getOrNull(0)?.toIntOrNull() ?: 0
            var totalSpots = spotsParts.getOrNull(1)?.toIntOrNull() ?: 0
            if (totalSpots==0) {
                totalSpots=10;
            }
            val spotsAvailable = totalSpots - spotsTaken

            //val (startTime, endTime) = parseTimes(timeText, dateText.ifBlank { "01-01-2024" })

            Result.success(
                TrainingDetails(
                    id = trainingId,
                    title = title,
                    instructor = instructor,
                    instructorLink = instructorLink,
                    location = location,
              //      startTime = startTime,
              //      endTime = endTime,
             //       classDate = dateText,
              //      classTime = timeText,
                    spotsAvailable = spotsAvailable,
                    totalSpots = totalSpots,
                    imageUrl = imageUrl,
                    description = description,
                    cost = cost,
                    isJoined = isJoined,
                    cancelPolicy = cancelPolicy
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findNextTrainingByTitle(
        title: String,
        weekday: String,
        startTime: String
    ): Result<TrainingDto?> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect("$baseUrl/classes?event_type=8")
                .cookies(cookies)
                .userAgent("Mozilla/5.0")
                .get()

            val trainings = mutableListOf<TrainingDto>()
            
            doc.select("#schedule_content .cal_column").forEach dayColumn@{ dayColumn ->
                val dayHeader = dayColumn.selectFirst(".day_head")
                val dayName = dayHeader?.selectFirst(".day_name_long")?.text()
                    ?: dayHeader?.selectFirst(".day_name_short")?.text()
                    ?: ""

                dayColumn.select(".class").forEach classElement@{ classElement ->
                    val className = classElement.selectFirst(".classname")?.text() ?: return@classElement
                    if (className.isBlank()) return@classElement

                    val classId = classElement.id()
                    val timeText = classElement.selectFirst(".time")?.text() ?: ""
                    val instructor = classElement.selectFirst(".instructor i")?.text() ?: "TBA"
                    val isFull = classElement.selectFirst(".full") != null
                    val isJoined = classElement.selectFirst("div.joined") != null

                    if (isFull && !isJoined) return@classElement

                    val eventDate = extractDateFromClass(classElement) ?: dayName
                    val (startTimeMillis, endTimeMillis) = parseTimes(timeText, eventDate)
                    val spotsAvailable = if (isFull && !isJoined) 0 else 10

                    if (classId.isNotBlank()) {
                        trainings.add(
                            TrainingDto(
                                id = classId,
                                title = className,
                                instructor = instructor,
                                startTime = startTimeMillis,
                                endTime = endTimeMillis,
                                location = "SwimGym",
                                spotsAvailable = spotsAvailable,
                                isJoined = isJoined,
                                classTime = timeText,
                                classDate = eventDate
                            )
                        )
                    }
                }
            }

            val weekdayIndex = getWeekdayIndex(weekday)
            val trainingsOnSameDay = trainings.filter { 
                val calendar = java.util.Calendar.getInstance()
                calendar.timeInMillis = it.startTime
                calendar.get(java.util.Calendar.DAY_OF_WEEK) == weekdayIndex
            }

            val nextTraining = trainingsOnSameDay.minByOrNull { 
                if (it.startTime > System.currentTimeMillis()) it.startTime else Long.MAX_VALUE 
            }

            Result.success(nextTraining)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getWeekdayIndex(weekday: String): Int {
        return when (weekday.lowercase().trim()) {
            "monday", "mon" -> java.util.Calendar.MONDAY
            "tuesday", "tue" -> java.util.Calendar.TUESDAY
            "wednesday", "wed" -> java.util.Calendar.WEDNESDAY
            "thursday", "thu" -> java.util.Calendar.THURSDAY
            "friday", "fri" -> java.util.Calendar.FRIDAY
            "saturday", "sat" -> java.util.Calendar.SATURDAY
            "sunday", "sun" -> java.util.Calendar.SUNDAY
            else -> java.util.Calendar.SUNDAY
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            client.newCall(
                buildRequest("$baseUrl/logout").build()
            ).execute()
            cookies = emptyMap()
            Result.success(Unit)
        } catch (e: Exception) {
            cookies = emptyMap()
            Result.success(Unit)
        }
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
                    val calendar = java.util.Calendar.getInstance()
                    calendar.time = startDate

                    calendar.set(java.util.Calendar.HOUR_OF_DAY, startTimeHours(startTime))
                    calendar.set(java.util.Calendar.MINUTE, startTimeMinutes(startTime))
                    val startMillis = calendar.timeInMillis

                    calendar.set(java.util.Calendar.HOUR_OF_DAY, startTimeHours(endTime))
                    calendar.set(java.util.Calendar.MINUTE, startTimeMinutes(endTime))
                    val endMillis = calendar.timeInMillis

                    return Pair(startMillis, endMillis)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(System.currentTimeMillis(), System.currentTimeMillis() + 3600000)
    }
    
    private fun startTimeHours(time: java.util.Date): Int {
        val cal = java.util.Calendar.getInstance()
        cal.time = time
        return cal.get(java.util.Calendar.HOUR_OF_DAY)
    }
    
    private fun startTimeMinutes(time: java.util.Date): Int {
        val cal = java.util.Calendar.getInstance()
        cal.time = time
        return cal.get(java.util.Calendar.MINUTE)
    }

    private fun parseCookie(cookieValue: String): Pair<String, String> {
        val parts = cookieValue.split(";")[0].split("=")
        return if (parts.size >= 2) {
            Pair(parts[0], parts[1])
        } else {
            Pair("", "")
        }
    }
}
