package com.swimgym.app.data.repository

import android.util.Log
import com.swimgym.app.data.api.VirtuagymApiClient
import com.swimgym.app.data.parser.ClubInfoParser
import com.swimgym.app.domain.model.ClubInfo
import com.swimgym.app.domain.repository.ClubInfoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Loads the SwimGym NEWS group messages and the club account credits table.
 *
 * The two pages are discovered from the portal's own navigation on the home
 * page so the app does not depend on a hard-coded member slug. A partial
 * failure (e.g. the group page is down) still returns the section that worked.
 */
class ClubInfoRepositoryImpl(
    private val apiClient: VirtuagymApiClient,
    private val parser: ClubInfoParser,
    private val baseUrl: String = "https://swimgym.virtuagym.com"
) : ClubInfoRepository {

    override suspend fun getClubInfo(): Result<ClubInfo> = withContext(Dispatchers.IO) {
        try {
            val home = apiClient.fetchHtml("$baseUrl/")

            var newsError: Throwable? = null
            var creditsError: Throwable? = null

            val news = runCatching {
                parser.parseNews(apiClient.fetchHtml(resolveNewsUrl(home)))
            }.getOrElse {
                newsError = it
                Log.w("ClubInfoRepo", "Failed to load SwimGym NEWS", it)
                emptyList()
            }

            val credits = runCatching {
                parser.parseCredits(apiClient.fetchHtml(resolveCreditsUrl(home)))
            }.getOrElse {
                creditsError = it
                Log.w("ClubInfoRepo", "Failed to load credits", it)
                emptyList()
            }

            if (news.isEmpty() && credits.isEmpty() && (newsError != null || creditsError != null)) {
                Result.failure(newsError ?: creditsError!!)
            } else {
                Result.success(ClubInfo(news = news, credits = credits))
            }
        } catch (e: Exception) {
            Log.e("ClubInfoRepo", "Failed to load club info", e)
            Result.failure(e)
        }
    }

    private fun resolveNewsUrl(home: Document): String {
        findLink(home) { link ->
            link.text().contains("SwimGym NEWS", ignoreCase = true) &&
                link.attr("href").contains("/groupid/")
        }?.let { return it }

        // Observed in tools/pages/SwimGym NEWS.htm. Used only when the portal
        // no longer exposes the group in its navigation.
        return DEFAULT_GROUP_PATH
    }

    private fun resolveCreditsUrl(home: Document): String {
        findLink(home) { link ->
            link.text().contains("Clubaccount", ignoreCase = true) &&
                link.attr("href").contains("/myservices")
        }?.let { return it }

        findLink(home) { it.attr("href").contains("/myservices") }?.let { return it }

        // Fall back to the signed-in member's profile link rather than a fixed
        // slug such as the "swimgymfinv" account used in the saved HTML dumps.
        val profile = findLink(home) { link ->
            link.text().contains("Mijn profiel", ignoreCase = true) &&
                link.attr("href").contains("/user/")
        }
        if (!profile.isNullOrBlank()) return "$profile/myservices"

        throw Exception("Club account information link not found")
    }

    private fun findLink(doc: Document, predicate: (Element) -> Boolean): String? =
        doc.select("a[href]").firstOrNull(predicate)
            ?.absUrl("href")
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val DEFAULT_GROUP_PATH = "https://swimgym.virtuagym.com/groupid/8794"
    }
}
