package com.swimgym.app.data.api

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Events

import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.InstructorEntity
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.model.BookingResponse
import com.swimgym.app.data.model.Mappers.toDomain
import com.swimgym.app.data.model.Mappers.toDto
import com.swimgym.app.data.model.Mappers.toEntity
import com.swimgym.app.data.model.TrainingDto
import com.swimgym.app.data.model.UserDto
import com.swimgym.app.data.repository.ScheduledBookingRepository
import com.swimgym.app.data.repository.ScheduledBookingStatus
import com.swimgym.app.data.repository.SessionRepository
import com.swimgym.app.domain.model.Training
import com.swimgym.app.util.BookingNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
class WebScraper(
    private val sessionRepository: SessionRepository,
    private val dao: SwimGymDao,
    private val scheduledBookingRepo: ScheduledBookingRepository,
    private val notificationManager: BookingNotificationManager,
    private val context: Context
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private var logintime: Long = 0
    private val baseUrl = "https://swimgym.virtuagym.com"
    private var cookies: Map<String, String> = emptyMap()
    private var mobileuserAgent: String = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome Mobile Safari/537.36"
    private var userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    suspend fun loadFromCache() {
        loadCookiesFromCache()
        loadUserAgentFromCache()
    }

    private fun triggerLoginRequired() {
        val now = System.currentTimeMillis()/1000
        if (logintime>0 && now-logintime<60) return
        logintime = now

       // cookies = emptyMap()
       // saveCookiesToCache()
        val intent = Intent(context, com.swimgym.app.receiver.LoginRequiredReceiver::class.java).apply {
            action = com.swimgym.app.receiver.LoginActivity.ACTION_LOGIN_REQUIRED
        }
        context.sendBroadcast(intent)
    }

    suspend fun setUserAgent(agent: String) {
        userAgent = agent
        saveUserAgentToCache()
    }

    suspend fun setCookiesAndSave(cookieMap: Map<String, String>) {
        cookies = cookieMap
        saveCookiesToCache()
    }

    suspend fun setCookiesAndUserAgent(cookieMap: Map<String, String>, userAgent: String) {
        setCookiesAndSave(cookieMap)
        setUserAgent(userAgent)
    }

     suspend fun loadCookiesFromCache() {
        cookies = sessionRepository.loadCookies()
        val cachedUserAgent = sessionRepository.loadUserAgent()
        if (cachedUserAgent != null) {
            userAgent = cachedUserAgent
        }
    }

    suspend fun saveCookiesToCache() {
        sessionRepository.saveCookies(cookies)
    }

    suspend fun saveUserAgentToCache() {
        sessionRepository.saveUserAgent(userAgent)
    }

    private suspend fun loadUserAgentFromCache() {
        val cachedUserAgent = sessionRepository.loadUserAgent()
        if (cachedUserAgent != null) {
            userAgent = cachedUserAgent
        }
    }

    private suspend fun buildRequest(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        if (cookies.isNullOrEmpty()) {
            loadFromCache()
        //           return Jsoup.parse("")
        }
        var cookieheader= ""
        for ((name, value) in cookies) {
            //if (name.contains("virtuagym")) {
                if (cookieheader.isNotBlank()) cookieheader+="; "
                cookieheader+= "$name=$value"
            //}
        }
        builder.addHeader("Cookie", cookieheader)
        builder.addHeader("User-Agent", userAgent)
        return builder
    }

    private suspend fun fetchHtml(url: String): org.jsoup.nodes.Document {
        val request = buildRequest(url)
            .method("GET", null)
            .build()

        val response = client.newCall(request).execute()
        extractCookiesFromResponse(response)

        val html = response.body?.string() ?: ""
        val doc = Jsoup.parse(html)
        if (doc.selectFirst(".menu-item.btn-login, .menu-item .btn-login") != null) {
            triggerLoginRequired()
            throw Exception("need to login")
        }
        return doc
    }

    private suspend fun extractCookiesFromResponse(response: okhttp3.Response) {
        var nextiscookie = false
        val newCookies = response.headers.filter {
            it.first.lowercase().equals("set-cookie")
        }.map {
            parseCookie(it.second)
        }

          /*  .filter { it.key.lowercase().startsWith("set-cookie") }
            .mapValues { it.value.firstOrNull() ?: "" }
            .map { parseCookie(it.value) }
            .toMap()
        */
        if (newCookies.isNotEmpty()) {
            cookies += newCookies
            saveCookiesToCache()
        }
    }

   suspend fun getSchedule(
        context: Context,
        weeks: Int = 3
    ): Result<List<TrainingDto>> = withContext(Dispatchers.IO) {
        try {
            val trainings = mutableListOf<TrainingDto>()
            val date = java.util.Calendar.getInstance()
            val now=date.timeInMillis/1000
            repeat(weeks) { weekOffset ->
                val formattedDate =LocalDate.now(ZoneId.systemDefault()) .minusWeeks(1-weekOffset.toLong()) .with(TemporalAdjusters.nextOrSame(
                    DayOfWeek.MONDAY))



          //      val formattedDate =
         //         SimpleDateFormat("yyyy-MM-dd", startofweek)

                val url = "$baseUrl/classes/week/$formattedDate?event_type=8"
                //val url = if (activityId.isNotEmpty()) "$base&$activityId" else base

                val doc = fetchHtml(url)
                
                doc.select("#schedule_content .cal_column").forEach dayColumn@{ dayColumn ->
                    val dayHeader = dayColumn.selectFirst(".day_head")
                    val dayName = dayHeader?.selectFirst(".day_name_long")?.text()
                        ?: dayHeader?.selectFirst(".day_name_short")?.text()
                        ?: ""

                    dayColumn.select(".class").forEach classElement@{ classElement ->
                        val className =
                            classElement.selectFirst(".classname")?.text() ?: return@classElement
                        if (className.isBlank()) return@classElement

                        val classId = classElement.id()
                        val timeText = classElement.selectFirst(".time")?.text() ?: ""
                        val instructor = classElement.selectFirst(".instructor i")?.text() ?: "TBA"
                        val isFull = classElement.selectFirst(".full")?.text() == "VOL"
                        val isJoined = classElement.selectFirst("div.joined") != null
                        val eventDate = extractDateFromClass(classElement) ?: dayName
                        val (startTime, endTime) = parseTimes(timeText, eventDate)
                        //val spotsAvailable = if (isFull && !isJoined) 0 else 1
                        if (classId.isNotBlank() && startTime>now) {
                            var training= TrainingEntity(
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
                            if (imageUrl==null) {
                                val trainingDetailsResult = getTrainingDetails(training, context)
                                cachedTraining = trainingDetailsResult.getOrNull()?.toDto()
                                imageUrl = cachedTraining?.imageUrl
                            }
                            if (imageUrl==null) {
                                imageUrl = ""
                            }
                            if(cachedTraining!=null) {
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
            }
            dao.insertTrainings(trainings.map { it.toEntity() })
            Result.success(trainings)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }


    suspend fun bookTraining(
        training: TrainingEntity,
        context: Context
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
                .add("class_name", training.title)
                .add("class_time", training.classTime)
                .add("class_date", training.classDate)
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
                buildRequest("$baseUrl/classes/class/${training.id}?event_type=8")
                    .post(formBody)
                    .build()
            ).execute()
            extractCookiesFromResponse(response)
            var r=getTrainingDetails(training,context)
            var t = r.getOrThrow()

            if (t.isJoined && (response.isSuccessful || response.code == 302)) {
                    try {
                        addTrainingToCalendar(training.toDomain(), context)
                    } catch (e: Exception) {
                        // warn user about calendar
                    }
                    Result.success(
                        BookingResponse(
                            id = 0,
                            trainingId = training.id,
                            userId = 0,
                            status = "confirmed",
                            className = training.title
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
        training: TrainingEntity,
        context: Context
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
                .add("class_name", training.title)
                .add("class_time", training.classTime)
                .add("class_date", training.classDate)
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
                buildRequest("$baseUrl/classes/class/${training.id}?event_type=8")
                    .post(formBody)
                    .build()
            ).execute()
            extractCookiesFromResponse(response)
            var r=getTrainingDetails(training,context)
            var t = r.getOrThrow()
            if (!t.isJoined && (response.isSuccessful || response.code == 302)) {
                // Remove from calendar when booking is canceled
                removeTrainingFromCalendar(training, context)
                
                Result.success(
                    BookingResponse(
                        id = 0,
                        trainingId = training.id,
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

    suspend fun getTrainingDetails(training: TrainingEntity,context: Context): Result<TrainingEntity> = withContext(Dispatchers.IO) {
        try {
            val trainingId = training.id
            val doc = fetchHtml("$baseUrl/classes/class/$trainingId?embedded=0")

            val title =
                doc.selectFirst(".modal-title-replacement, .class-info .class-name")?.text() ?: ""
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
            if (imageUrl.isNotEmpty()) {
                dao.insertInstructor(
                    InstructorEntity(
                        instructorName = instructor,
                        instructorLink = instructorLink,
                        instructorImage = imageUrl
                    )
                )
            }

            //val (startTime, endTime) = parseTimes(timeText, dateText.ifBlank { "01-01-2024" })
      //      var t = dao.getTrainingById(trainingId)
       //     if (t != null) {
            var tupdate = TrainingEntity(
                id = trainingId,
                instructor = instructor,
                startTime = training.startTime,
                endTime = training.endTime,
                classDate = training.classDate,
                classTime = training.classTime,
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

            dao.insertTraining(tupdate)
        //}
            Result.success(tupdate)
/*
                Result.success(
                TrainingDetails(
                    id = trainingId,
                    title = title,
                    instructor = instructor,
                    location = location,
                    spotsAvailable = spotsAvailable,
                    totalSpots = totalSpots,
                    description = description,
                    isFull = isFull,
                    cost = cost,
                    isJoined = isJoined,
                    cancelPolicy = cancelPolicy
                )
            )
            */
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            cookies = emptyMap()
            saveCookiesToCache()
            Result.success(Unit)
        } catch (e: Exception) {
            cookies = emptyMap()
            saveCookiesToCache()
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
                    val start = calendar.timeInMillis/1000

                    calendar.set(java.util.Calendar.HOUR_OF_DAY, startTimeHours(endTime))
                    calendar.set(java.util.Calendar.MINUTE, startTimeMinutes(endTime))
                    val end = calendar.timeInMillis/1000

                    return Pair(start, end)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(0, 3600)
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


    suspend fun checkBookings(context: Context) {
        val calendar = Calendar.getInstance()
        val now = calendar.time.time/1000
        val bookings = scheduledBookingRepo.getAllBookings()
        if (bookings.isNullOrEmpty()) return
        var trainings = dao.getAllTrainingsList();
        if (trainings.isNullOrEmpty()) {
            return
        }

        var activeBookings = bookings.filter { it.status == ScheduledBookingStatus.ACTIVE }

        activeBookings.forEach { booking ->
            // make sure the startTime is in the future
            while(booking.startTime<now) {
                booking.startTime+=7*86400
            }
            processBooking(booking, trainings,context)
        }

    //    ListenableWorker.Result.success()
    }

    fun getNextTraining(
    startTime: Long,
    title: String,
    trainings: List<TrainingEntity>
): TrainingEntity? {
    val calendar = Calendar.getInstance()
    val now=calendar.time.time/1000
    var nexttime=startTime;
    val week = 7 * 86400
    while (nexttime<now) {
        nexttime+=week
    }
    if (nexttime>now+week) {
        // bug,cannot book more than a week in advance
        return null
    }
    if (nexttime<now+86400) {
        // cannot book less than a day in advance
        return null
    }

        for (training in trainings) {
            if (training.startTime == nexttime) {
                if (training.title.equals(title, ignoreCase = true)) { return training
                } else {
                    // warn user training is renamed return null
                }
            }
        }
    // maybe get new schedule?
        return null
    }

    suspend fun addTrainingToCalendar(training: Training, context: Context): String? {
            try {
                // Get the selected calendar ID from SessionRepository
                val sessionRepository = SessionRepository(context)
                val selectedCalendarId = sessionRepository.selectedCalendarId.first() ?: 1L
                if (selectedCalendarId<1) {
                    // no calendar selected
                    return null
                }
                // Get reminder settings
                val reminderMinutes = sessionRepository.getReminderMinutes()
                val reminderEnabled = sessionRepository.getReminderEnabled()
                
                val values = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, training.startTime*1000)
                    put(CalendarContract.Events.DTEND, training.endTime*1000)
                    put(CalendarContract.Events.TITLE, training.title)
                    put(
                        CalendarContract.Events.DESCRIPTION,
                        "${training.instructor}\n${training.location}"
                    )
                    put(
                        CalendarContract.Events.EVENT_LOCATION,
                        "Swimgym, Wibautstraat 131b, 1091 GL Amsterdam"
                    )
                    put(CalendarContract.Events.CALENDAR_ID, selectedCalendarId)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    if (reminderEnabled && reminderMinutes > 0) {
                        put(Events.HAS_ALARM, true);
                    }
                }
                
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                val eventId = uri?.getLastPathSegment() ?: return null

                // Store eventId and calendarId in the training entity
                val trainingEntity = training.toEntity()
                val updatedTraining = trainingEntity.copy(
                    eventId = eventId,
                    calendarId = selectedCalendarId.toString()
                )
                dao.insertTraining(updatedTraining)

                // Add reminder if enabled
                if (reminderEnabled && reminderMinutes > 0) {
                    val reminderValues = ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, eventId.toLong())
                        put(CalendarContract.Reminders.MINUTES, reminderMinutes)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    }
                    context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                }

            return eventId

            } catch (e: Exception) {
                 e.printStackTrace()
                return null
             }
        }

    suspend fun removeTrainingFromCalendar(training: TrainingEntity, context: Context) {
        try {
            val eventId = training.eventId
            if (eventId.isBlank()) {
                return
            }

            val calendarId = training.calendarId.toLongOrNull() ?: return

            // Delete the event from the calendar
            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "_id=? AND calendar_id=?",
                arrayOf(eventId, calendarId.toString())
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun processBooking(booking: com.swimgym.app.data.repository.ScheduledBooking, trainings: List<TrainingEntity>, context: Context) {
        try {
            val shouldBook = shouldBookNow(booking)
            if (!shouldBook) return
            val training = getNextTraining(booking.startTime, booking.className, trainings) ?: return
            val res = getTrainingDetails(training,context)
            val updtraining = res.getOrThrow()
            if (updtraining.isJoined) {
                // training is already booked, skip to next
                scheduledBookingRepo.incrementBookingCount(booking.id, training)
                return
            }
            if (updtraining.isFull) {
                // training is full, retry later
                return
            }
            val result = bookTraining(training, context)
            result.fold(
                onSuccess = {
                    scheduledBookingRepo.incrementBookingCount(booking.id, training)

                    val maxRepeat = booking.maxRepeatCount
                    if (maxRepeat != null && booking.bookedCount + 1 >= maxRepeat) {
                        scheduledBookingRepo.completeBooking(booking.id)
                        return
                    }
                    notificationManager.showBookingConfirmation(
                        trainingName = training.title,
                        classTime = training.classTime,
                        classDate = training.classDate,
                        isScheduledBooking = true
                    )
                },
                onFailure = {
                    // Don't retry here, will be checked again in 30 minutes
                }
            )
        } catch (e: Exception) {
            // Error handled silently, will retry on next periodic run
        }
    }

    private fun shouldBookNow(booking: com.swimgym.app.data.repository.ScheduledBooking): Boolean {
        val calendar = Calendar.getInstance()
        val now=calendar.time.time/1000
        val next=booking.startTime //-(7 * 86400)
        val diff=(next-now)/86400
        //if (diff<7)
        return next < now + 7 * 86400 && next>now+86400
    }
}
