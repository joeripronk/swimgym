package com.swimgym.app.data.api

import android.content.Context
import com.swimgym.app.data.repository.SessionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.toLongOrDefault
import java.util.concurrent.TimeUnit

interface VirtuagymApiClient {
    suspend fun fetchHtml(url: String): org.jsoup.nodes.Document
    suspend fun fetchPage(url: String): String
    suspend fun postBooking(url: String, body: FormBody): Response
    suspend fun postCancel(url: String, body: FormBody): Response
    fun isLoggedIn(): Boolean
}

class VirtuagymApiClientImpl(
    private val sessionRepository: SessionRepository,
    private val notificationManager: com.swimgym.app.util.BookingNotificationManager,
    private val context: Context
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
    private var userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    override suspend fun fetchHtml(url: String): org.jsoup.nodes.Document = cookieLock.withLock {
        loadCookiesFromCache()
        if (!isLoggedIn()) {
            throw Exception("not logged in")
        }
        val request = buildRequest(url)
            .method("GET", null)
            .build()

        val response = client.newCall(request).execute()
        extractCookiesFromResponse(response)

        val html = response.body?.string() ?: ""
        val doc = org.jsoup.Jsoup.parse(html)
        if (doc.selectFirst(".menu-item.btn-login, .menu-item .btn-login") != null) {
            triggerLoginRequired()
            throw Exception("need to login")
        }
        return doc
    }

    override suspend fun fetchPage(url: String): String = cookieLock.withLock {
        loadCookiesFromCache()
        if (!isLoggedIn()) {
            throw Exception("not logged in")
        }
        val request = buildRequest(url)
            .method("GET", null)
            .build()

        val response = client.newCall(request).execute()
        extractCookiesFromResponse(response)
        return response.body?.string() ?: ""
    }

    override suspend fun postBooking(url: String, body: FormBody): Response = cookieLock.withLock {
        loadCookiesFromCache()
        val request = buildRequest(url)
            .post(body)
            .build()
        val response = client.newCall(request).execute()
        extractCookiesFromResponse(response)
        return response
    }

    override suspend fun postCancel(url: String, body: FormBody): Response = cookieLock.withLock {
        loadCookiesFromCache()
        val request = buildRequest(url)
            .post(body)
            .build()
        val response = client.newCall(request).execute()
        extractCookiesFromResponse(response)
        return response
    }

    override fun isLoggedIn(): Boolean = cookiesLoggedIn(cookies)

    private suspend fun triggerLoginRequired() {
        val now = System.currentTimeMillis() / 1000
        if (logintime > 0 && now - logintime < 60) return
        logintime = now
        notificationManager.showLoginRequired()
        val intent = android.content.Intent(context, com.swimgym.app.receiver.LoginRequiredReceiver::class.java).apply {
            action = com.swimgym.app.receiver.LoginActivity.ACTION_LOGIN_REQUIRED
        }
        context.sendBroadcast(intent)
    }

    private suspend fun loadCookiesFromCache() {
        val newCookies = sessionRepository.loadCookies()
        if (cookiesLoggedIn(newCookies)) {
            cookies = newCookies
        }
    }

    private suspend fun saveCookiesToCache(cookieMap: Map<String, String>) {
        if (cookiesLoggedIn(cookieMap)) {
            sessionRepository.saveCookies(cookieMap)
            cookies = cookieMap
        }
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
        try {
            var lid: Long = 0
            if (cookieMap.contains("virtuagym_u")) {
                val uid = cookieMap["virtuagym_u"]
                lid = uid?.toLongOrDefault(1L)!!
            }
            return lid > 1
        } catch (e: Exception) {
        }
        return false
    }

    private suspend fun extractCookiesFromResponse(response: Response) {
        val cookieMap = cookies.toMutableMap()
        val newCookies: Map<String, String> = response.headers.filter {
            it.first.lowercase().equals("set-cookie")
        }.associate {
            parseCookie(it.second)
        }

        cookieMap += newCookies
        if (!cookiesLoggedIn(cookieMap)) {
            triggerLoginRequired()
            throw Exception("you are logged out")
        }
        saveCookiesToCache(cookieMap)
    }

    private fun parseCookie(cookieValue: String): Pair<String, String> {
        val part = cookieValue.split(";")[0]
        val idx = part.indexOf('=')
        return if (idx >= 0) {
            Pair(part.substring(0, idx), part.substring(idx + 1))
        } else {
            Pair("", "")
        }
    }
}
