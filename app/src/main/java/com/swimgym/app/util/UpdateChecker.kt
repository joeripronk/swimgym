package com.swimgym.app.util

import com.swimgym.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val body: String,
    val apkDownloadUrl: String? = null
)

object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/joeripronk/swimgym/releases/latest"
    private const val APK_ASSET_PATTERN = "swimgym-.*\\.apk"

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
            val assets = json.optJSONArray("assets")
            val apkDownloadUrl = if (assets != null) {
                findApkAsset(assets)
            } else {
                null
            }

            val releaseInfo = ReleaseInfo(
                tagName = json.getString("tag_name"),
                htmlUrl = json.getString("html_url"),
                body = json.optString("body", ""),
                apkDownloadUrl = apkDownloadUrl
            )
            Result.success(releaseInfo)
        }

    private fun findApkAsset(assets: JSONArray): String? {
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.matches(Regex(APK_ASSET_PATTERN))) {
                return asset.getString("browser_download_url")
            }
        }
        return null
    }

    suspend fun downloadApk(context: android.content.Context, downloadUrl: String): Result<File> =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(downloadUrl)
                .header("Accept", "application/octet-stream")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Download failed: HTTP ${response.code}"))
            }

            val cacheDir = context.cacheDir
            val apkDir = File(cacheDir, "apk")
            apkDir.mkdirs()
            val apkFile = File(apkDir, "swimgym-release.apk")

            val body = response.body
            if (body == null) {
                return@withContext Result.failure(IOException("No response body"))
            }

            FileOutputStream(apkFile).use { out ->
                body.byteStream().use { input ->
                    input.copyTo(out)
                }
            }

            Result.success(apkFile)
        }

    fun isNewerVersion(current: String, latest: String): Boolean {
        val currentClean = current.trimStart('v')
        val latestClean = latest.trimStart('v')
        return compareVersions(currentClean, latestClean) < 0
    }

    private fun compareVersions(current: String, latest: String): Int {
        val currentParts = current.split(".").map { it.trim() }
        val latestParts = latest.split(".").map { it.trim() }
        val maxLen = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { "0" }
            val l = latestParts.getOrElse(i) { "0" }
            val cNum = parseVersionPart(c)
            val lNum = parseVersionPart(l)
            if (cNum < lNum) return -1
            if (cNum > lNum) return 1
        }
        return 0
    }

    private fun parseVersionPart(part: String): Int {
        val clean = part.trimStart('v')
        val pIndex = clean.indexOf('p')
        return if (pIndex >= 0) {
            clean.substring(pIndex + 1).toIntOrNull() ?: 0
        } else {
            clean.toIntOrNull() ?: 0
        }
    }
}
