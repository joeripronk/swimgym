package com.swimgym.app.data.api

import android.content.Context
import com.swimgym.app.data.repository.SessionRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface VirtuagymApiClient {
    suspend fun fetchHtml(url: String): org.jsoup.nodes.Document

    /**
     * Fetches several pages with one cookie load/lock. Results are positional
     * so a single failed URL does not discard the others.
     */
    suspend fun fetchHtmlBatch(urls: List<String>): List<Result<org.jsoup.nodes.Document>> =
        urls.map { url -> runCatching { fetchHtml(url) } }

    suspend fun fetchPage(url: String): String
    suspend fun postBooking(url: String, body: FormBody): Response
    suspend fun postCancel(url: String, body: FormBody): Response
    suspend fun bookTraining(training: com.swimgym.app.data.local.entity.TrainingEntity): Result<Unit>
    suspend fun cancelTraining(training: com.swimgym.app.data.local.entity.TrainingEntity): Result<Unit>
    fun isLoggedIn(): Boolean
}

class VirtuagymApiClientImpl(
    private val sessionRepository: SessionRepository,
    private val notificationManager: com.swimgym.app.util.BookingNotificationManager,
    private val context: Context,
    private val calendarService: com.swimgym.app.data.repository.CalendarService
) : VirtuagymApiClient {

    private val cookieLock = Mutex()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var cookies: Map<String, String> = emptyMap()
    private var logintime: Long = 0
    private val baseUrl = "https://swimgym.virtuagym.com"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private suspend fun executeRequest(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    override suspend fun fetchHtml(url: String): org.jsoup.nodes.Document = cookieLock.withLock {
        loadCookiesFromCache()
        if (!isLoggedIn()) {
            throw Exception("not logged in")
        }
        val request = buildRequest(url)
            .method("GET", null)
            .build()

        val response = executeRequest(request)
        extractCookiesFromResponse(response)

        val html = response.body?.string() ?: ""
        if (isLoginPage(html)) {
            response.close()
            triggerLoginRequired()
            throw Exception("need to login")
        }
        // Keep the request URL as the document base so parsers can resolve
        // relative avatar/detail links with absUrl() instead of guessing.
        return org.jsoup.Jsoup.parse(html, url)
    }

    override suspend fun fetchHtmlBatch(urls: List<String>): List<Result<org.jsoup.nodes.Document>> =
        cookieLock.withLock {
            if (urls.isEmpty()) return@withLock emptyList()
            loadCookiesFromCache()
            if (!isLoggedIn()) {
                return@withLock urls.map { Result.failure(Exception("not logged in")) }
            }
            val results = urls.map { url ->
                runCatching {
                    val request = buildRequest(url).method("GET", null).build()
                    val response = executeRequest(request)
                    // Update the in-memory jar per response, persist once at the end.
                    extractCookiesFromResponse(response, persist = false)
                    val html = response.body?.string().orEmpty()
                    if (isLoginPage(html)) {
                        response.close()
                        triggerLoginRequired()
                        throw Exception("need to login")
                    }
                    org.jsoup.Jsoup.parse(html, url)
                }
            }
            saveCookiesToCache(cookies)
            results
        }

    override suspend fun fetchPage(url: String): String = cookieLock.withLock {
        loadCookiesFromCache()
        if (!isLoggedIn()) {
            throw Exception("not logged in")
        }
        val request = buildRequest(url)
            .method("GET", null)
            .build()

        val response = executeRequest(request)
        extractCookiesFromResponse(response)
        return response.body?.string() ?: ""
    }

    override suspend fun postBooking(url: String, body: FormBody): Response = cookieLock.withLock {
        loadCookiesFromCache()
        val request = buildRequest(url)
            .post(body)
            .build()
        val response = executeRequest(request)
        // Do not close the response here: callers own the returned body.
        extractCookiesFromResponse(response)
        return response
    }

    override suspend fun postCancel(url: String, body: FormBody): Response = cookieLock.withLock {
        loadCookiesFromCache()
        val request = buildRequest(url)
            .post(body)
            .build()
        val response = executeRequest(request)
        extractCookiesFromResponse(response)
        return response
    }

    private fun buildBookingForm(
        training: com.swimgym.app.data.local.entity.TrainingEntity,
        action: String,
        absentReason: String
    ): FormBody = FormBody.Builder()
        .add("action", action)
        .add("absent_reason", absentReason)
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

    override suspend fun bookTraining(training: com.swimgym.app.data.local.entity.TrainingEntity): Result<Unit> = cookieLock.withLock {
        loadCookiesFromCache()
        val url = "$baseUrl/classes/class/${training.id}?event_type=${training.eventType}"
        val formBody = buildBookingForm(training, "reserve_class", "")
        val request = buildRequest(url).post(formBody).build()
        val response = executeRequest(request)
        val html = response.body?.string().orEmpty()
        extractCookiesFromResponse(response)
        if (!response.isSuccessful) {
            return@withLock Result.failure(Exception("Booking failed: ${response.code}"))
        }
        if (isLoginPage(html)) {
            triggerLoginRequired()
            return@withLock Result.failure(Exception("need to login"))
        }
        if (classJoinedState(training.id) != true) {
            return@withLock Result.failure(Exception("Booking was not confirmed"))
        }
        calendarService.addTrainingToCalendar(training)
        Result.success(Unit)
    }

    override suspend fun cancelTraining(training: com.swimgym.app.data.local.entity.TrainingEntity): Result<Unit> = cookieLock.withLock {
        loadCookiesFromCache()
        val url = "$baseUrl/classes/class/${training.id}?event_type=${training.eventType}"
        val formBody = buildBookingForm(training, "cancel_reserve_class", "unknown")
        val request = buildRequest(url).post(formBody).build()
        val response = executeRequest(request)
        val html = response.body?.string().orEmpty()
        extractCookiesFromResponse(response)
        if (!response.isSuccessful) {
            return@withLock Result.failure(Exception("Cancel failed: ${response.code}"))
        }
        if (isLoginPage(html)) {
            triggerLoginRequired()
            return@withLock Result.failure(Exception("need to login"))
        }
        if (classJoinedState(training.id) != false) {
            return@withLock Result.failure(Exception("Cancellation was not confirmed"))
        }
        Result.success(Unit)
    }

    override fun isLoggedIn(): Boolean = cookiesLoggedIn(cookies)

    private fun isLoginPage(html: String): Boolean =
        org.jsoup.Jsoup.parse(html).selectFirst(".menu-item.btn-login, .menu-item .btn-login") != null

    private suspend fun classJoinedState(trainingId: String): Boolean? {
        val request = buildRequest("$baseUrl/classes/class/$trainingId?embedded=0")
            .method("GET", null)
            .build()
        val response = executeRequest(request)
        val html = response.body?.string().orEmpty()
        if (!response.isSuccessful || isLoginPage(html)) return null
        val doc = org.jsoup.Jsoup.parse(html)
        if (doc.selectFirst(".event-actions") == null) return null
        return doc.selectFirst(".booking-text.green") != null
    }

    private suspend fun triggerLoginRequired() {
        val now = System.currentTimeMillis() / 1000
        if (logintime > 0 && now - logintime < 60) return
        logintime = now
        notificationManager.showLoginRequired()
        val intent = android.content.Intent(context, com.swimgym.app.receiver.LoginRequiredReceiver::class.java).apply {
            action = com.swimgym.app.receiver.LoginActivity.ACTION_LOGIN_REQUIRED
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent) // NOSONAR: explicit component + package scoped broadcast
    }

    private suspend fun loadCookiesFromCache() {
        cookies = sessionRepository.loadCookies()
    }

    private suspend fun saveCookiesToCache(cookieMap: Map<String, String>) {
        if (cookiesLoggedIn(cookieMap)) {
            sessionRepository.saveCookies(cookieMap)
            cookies = cookieMap
        }
    }

    private suspend fun clearCookiesFromCache() {
        cookies = emptyMap()
        sessionRepository.clearCookies()
    }

    private fun buildRequest(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        var cookieHeader = ""
        for ((name, value) in cookies) {
            if (cookieHeader.isNotBlank()) cookieHeader += "; "
            cookieHeader += "$name=$value"
        }
        builder.addHeader("Cookie", cookieHeader)
        builder.addHeader("User-Agent", userAgent)
        return builder
    }

    private fun cookiesLoggedIn(cookieMap: Map<String, String>): Boolean {
        val uid = cookieMap["virtuagym_u"]?.toLongOrNull() ?: 0L
        return uid > 1
    }

    private suspend fun extractCookiesFromResponse(response: Response, persist: Boolean = true) {
        val cookieMap = cookies.toMutableMap()
        // Walk the redirect chain oldest -> newest so fresh cookies win over
        // cookies set by an earlier hop.
        val chain = mutableListOf<Response>()
        var current: Response? = response
        while (current != null) {
            chain.add(current)
            current = current.priorResponse
        }
        for (hop in chain.asReversed()) {
            hop.headers("Set-Cookie").forEach { header ->
                val parsed = parseCookie(header) ?: return@forEach
                val (name, value) = parsed
                // A null value means the cookie was explicitly expired by the
                // server; drop it instead of storing an empty auth token.
                if (value == null) cookieMap.remove(name) else cookieMap[name] = value
            }
        }
        if (!cookiesLoggedIn(cookieMap)) {
            // Persist the logged-out state, otherwise the stale session cookie
            // is reloaded on the next call and the app never leaves "logged in".
            response.close()
            clearCookiesFromCache()
            triggerLoginRequired()
            throw Exception("you are logged out")
        }
        cookies = cookieMap
        if (persist) saveCookiesToCache(cookieMap)
    }

    /** Returns (name, value), or (name, null) for an expired cookie. */
    private fun parseCookie(header: String): Pair<String, String?>? {
        val parts = header.split(";").map { it.trim() }
        val nameValue = parts.firstOrNull() ?: return null
        val idx = nameValue.indexOf('=')
        if (idx <= 0) return null
        val name = nameValue.substring(0, idx)
        val value = nameValue.substring(idx + 1)
        val expired = parts.any { attribute ->
            val attr = attribute.trim()
            when {
                attr.startsWith("Max-Age=", ignoreCase = true) ->
                    attr.substringAfter('=').trim().toLongOrNull()?.let { it <= 0 } == true
                attr.startsWith("Expires=", ignoreCase = true) ->
                    isPastExpires(attr.substringAfter('=').trim())
                else -> false
            }
        }
        return name to if (expired) null else value
    }

    private fun isPastExpires(value: String): Boolean {
        for (pattern in listOf(
            "EEE, dd MMM yyyy HH:mm:ss 'GMT'",
            "EEE, dd-MMM-yy HH:mm:ss 'GMT'"
        )) {
            try {
                val format = java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("GMT")
                }
                val expires = format.parse(value)?.time ?: continue
                return expires <= System.currentTimeMillis()
            } catch (_: Exception) {
                // Try the next supported date pattern.
                continue
            }
        }
        return false
    }
}
