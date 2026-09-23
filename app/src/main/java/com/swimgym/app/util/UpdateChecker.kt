package com.swimgym.app.util

import com.swimgym.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val body: String
)

object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/joeripronk/swimgym/releases/latest"

    suspend fun checkForUpdates(context: android.content.Context): Result<ReleaseInfo> =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}"))
            }

            val json = JSONObject(response.body?.string() ?: "")
            val releaseInfo = ReleaseInfo(
                tagName = json.getString("tag_name"),
                htmlUrl = json.getString("html_url"),
                body = json.optString("body", "")
            )
            Result.success(releaseInfo)
        }

    fun isNewerVersion(current: String, latest: String): Boolean {
        return current != latest && current != "1.0"
    }
}
