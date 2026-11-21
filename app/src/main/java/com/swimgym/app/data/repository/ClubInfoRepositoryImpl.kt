package com.swimgym.app.data.repository

import android.util.Log
import com.swimgym.app.data.api.VirtuagymApiClient
import com.swimgym.app.data.parser.ClubInfoParser
import com.swimgym.app.domain.model.ClubInfo
import com.swimgym.app.domain.model.ClubNews
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

            var news: List<ClubNews> = emptyList()
            // The canonical group URL is /group/swimgym-portal; /groupid/<id>
            // only redirects there, so try the canonical URL first.
            for (url in listOf(DEFAULT_GROUP_SLUG_URL, resolveNewsUrl(home)).distinct()) {
                try {
                    news = parser.parseNews(apiClient.fetchHtml(url))
                    if (news.isNotEmpty()) {
                        newsError = null
                        break
                    }
                } catch (e: Exception) {
                    newsError = e
                    Log.w("ClubInfoRepo", "Failed to load SwimGym NEWS from $url", e)
                }
            }
            Log.d("ClubInfoRepo", "Loaded ${news.size} SwimGym NEWS items")

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
        // Slug used by the group page's own AJAX endpoints in the saved dump.
        const val DEFAULT_GROUP_SLUG_URL = "https://swimgym.virtuagym.com/group/swimgym-portal"
    }
}
