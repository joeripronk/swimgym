package com.swimgym.app.util

import com.swimgym.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

data class ReleaseInfo(
    val tagName: String,
    val htmlUrl: String,
    val body: String,
    val apkDownloadUrl: String? = null,
    val apkSha256: String? = null
)

object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/joeripronk/swimgym/releases/latest"
    private const val APK_ASSET_PATTERN = "swimgym-.*\\.apk"
    private val DOWNLOAD_HOSTS = setOf("github.com", "objects.githubusercontent.com")
    private val PAGE_HOSTS = setOf("github.com")

    private val hostAllowlist = Interceptor { chain ->
        val url = chain.request().url
        if (url.scheme != "https" || url.host !in DOWNLOAD_HOSTS) {
            throw IOException("Blocked download host: ${url.host}")
        }
        chain.proceed(chain.request())
    }

    private fun isAllowedUrl(value: String, hosts: Set<String>): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host in hosts
    }

    fun isSafeGitHubUrl(value: String): Boolean = isAllowedUrl(value, PAGE_HOSTS)

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

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                val body = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response body"))

                return@withContext try {
                    val json = JSONObject(body)
                    val apkAsset = json.optJSONArray("assets")?.let { findApkAsset(it) }
                    Result.success(
                        ReleaseInfo(
                            tagName = json.getString("tag_name"),
                            htmlUrl = json.getString("html_url"),
                            body = json.optString("body", ""),
                            apkDownloadUrl = apkAsset?.first,
                            apkSha256 = apkAsset?.second
                        )
                    )
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

    private fun findApkAsset(assets: JSONArray): Pair<String, String?>? {
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.matches(Regex(APK_ASSET_PATTERN))) {
                val digest = asset.optString("digest", "")
                    .removePrefix("sha256:")
                    .takeIf { it.isNotBlank() }
                return asset.getString("browser_download_url") to digest
            }
        }
        return null
    }

    suspend fun downloadApk(
        context: android.content.Context,
        downloadUrl: String,
        sha256: String?
    ): Result<File> = withContext(Dispatchers.IO) {
            if (!isAllowedUrl(downloadUrl, DOWNLOAD_HOSTS)) {
                return@withContext Result.failure(IOException("Download host is not allowed"))
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor(hostAllowlist)
                .build()

            val request = Request.Builder()
                .url(downloadUrl)
                .header("Accept", "application/octet-stream")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Download failed: HTTP ${response.code}"))
                }

                val cacheDir = context.cacheDir
                val apkDir = File(cacheDir, "apk")
                apkDir.mkdirs()
                val apkFile = File(apkDir, "swimgym-release.apk")

                val body = response.body
                    ?: return@withContext Result.failure(IOException("No response body"))

                try {
                    FileOutputStream(apkFile).use { out ->
                        body.byteStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                } catch (e: IOException) {
                    return@withContext Result.failure(e)
                }

                if (sha256.isNullOrBlank()) {
                    apkFile.delete()
                    return@withContext Result.failure(IOException("Release has no APK checksum"))
                }
                val actual = apkFile.sha256()
                if (!actual.equals(sha256, ignoreCase = true)) {
                    apkFile.delete()
                    return@withContext Result.failure(IOException("APK checksum mismatch"))
                }

                Result.success(apkFile)
            }
        }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
    }

    fun isNewerVersion(current: String, latest: String): Boolean {
        val c = parseVersion(current)
        val l = parseVersion(latest)
        return compareValuesBy(c, l,
            { it.major }, { it.minor }, { it.patch }
        ) < 0
    }

    private data class VersionParts(val major: Int, val minor: Int, val patch: Int)

    private fun parseVersion(value: String): VersionParts {
        val clean = value.trim().trimStart('v')
        val match = Regex("""(\d+)\.(\d+)p(\d+)""").find(clean)
        if (match != null) {
            return VersionParts(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt()
            )
        }
        val parts = clean.split(".", "p").mapNotNull { it.toIntOrNull() }
        return VersionParts(
            parts.getOrElse(0) { 0 },
            parts.getOrElse(1) { 0 },
            parts.getOrElse(2) { 0 }
        )
    }
}
