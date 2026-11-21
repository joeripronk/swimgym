package com.swimgym.app.data.parser

import android.util.Log
import com.swimgym.app.domain.model.ClubNews
import com.swimgym.app.domain.model.CreditItem
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

interface ClubInfoParser {
    fun parseNews(doc: Document): List<ClubNews>
    fun isLastNewsPage(doc: Document): Boolean
    fun parseCredits(doc: Document): List<CreditItem>
}

/**
 * Parses the two Virtuagym pages captured in tools/pages/:
 *  - SwimGym NEWS group messages (`#group_messages .ui-message`, with the
 *    activity stream as a fallback), loaded from /groupid/<id>
 *  - Credits in the club account "Credits" table (`table.credits`)
 *
 * Both parsers skip empty/placeholder rows and degrade gracefully when the
 * portal changes its markup, so one broken section does not hide the other.
 */
class ClubInfoParserImpl : ClubInfoParser {

    override fun parseNews(doc: Document): List<ClubNews> {
        val messages = doc.select("#group_messages .ui-message").mapNotNull(::parseGroupMessage)
        val knownIds = messages.map { it.id }.toSet()
        // The activity stream can still be the only source on some responses:
        // it is loaded separately and sometimes contains rows the message list
        // does not. Skip stubs we already have as full messages.
        val streamItems = doc.select(".activity-stream-row").mapNotNull(::parseNewsRow)
            .filterNot { it.id in knownIds }
        val result = messages + streamItems
        Log.d(
            "ClubInfoParser",
            "Parsed news: ${messages.size} group messages, ${streamItems.size} activity rows"
        )
        return result
    }

    /**
     * Virtuagym reports the end of the activity stream in the response itself:
     * is_last_result_queue["activitystream"] is 0 while more pages exist and
     * -1/1 on the last one. Absent flag means "unknown", never "last".
     */
    override fun isLastNewsPage(doc: Document): Boolean {
        val value = LAST_RESULT_FLAG.find(doc.html())?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return false
        return value != 0
    }

    private fun parseGroupMessage(message: Element): ClubNews? {
        val content = message.selectFirst(".content") ?: return null
        val body = content.clone()
        body.select("script, style").remove()
        val text = body.html()
            .replace(Regex("(?i)<br\\s*/?>|</p>|</div>"), LINE_BREAK)
            .let { Jsoup.parseBodyFragment(it).text() }
            .replace(LINE_BREAK, "\n")
            .replace(Regex("[ \\t]*\\n[ \\t]*"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .trim('"', '\u201C', '\u201D', '\u201E')
        if (text.isBlank()) return null

        val header = message.selectFirst(".message .header")
        val author = header?.selectFirst(".user-name a")?.text()?.trim()
            ?: header?.selectFirst(".ui-profile-pic[title]")?.attr("title")
            ?: ""
        val avatar = header?.selectFirst(".user-image img")
        val dateLink = header?.selectFirst(".message-date a[title]")
        val id = message.id().removePrefix("message_")
            .takeIf { it.isNotBlank() }
            ?: dateLink?.attr("href")?.substringAfterLast("/", "")
            ?: ""

        return ClubNews(
            id = id,
            author = author,
            authorImageUrl = avatar?.absUrl("src")?.takeIf { it.isNotBlank() }
                ?: avatar?.attr("src") ?: "",
            message = text,
            dateLabel = dateLink?.text()?.trim() ?: "",
            timestamp = dateLink?.attr("title")?.let(::parseDate) ?: 0L,
            url = dateLink?.absUrl("href") ?: "",
            imageUrl = message.selectFirst(".media a[href]")?.absUrl("href") ?: ""
        )
    }

    private fun parseNewsRow(row: Element): ClubNews? {
        val quote = row.selectFirst(".activity-stream-quote") ?: return null
        val message = quote.html()
            .replace(Regex("(?i)<br\\s*/?>"), LINE_BREAK)
            .let { Jsoup.parseBodyFragment(it).text() }
            .replace(LINE_BREAK, "\n")
            .replace(Regex("[ \\t]*\\n[ \\t]*"), "\n")
            .trim()
            .trim('"', '\u201C', '\u201D', '\u201E')
        if (message.isBlank()) return null

        val text = row.selectFirst(".activity-stream-text")
        val messageLink = text?.selectFirst("a[href*=/msgid/]")
        val author = text?.selectFirst("a")
            ?.text()?.takeIf { it.isNotBlank() }
            ?: row.selectFirst(".ui-profile-pic[title]")?.attr("title")
            ?: ""

        val dateElement = row.selectFirst(".activity-stream-date [title]")
        val dateLabel = row.selectFirst(".activity-stream-date")?.text()?.trim() ?: ""
        val timestamp = dateElement?.attr("title")?.let(::parseDate) ?: 0L

        val avatar = row.selectFirst(".activity-stream-avatar img")
        val id = messageLink?.attr("href")
            ?.substringAfterLast("/msgid/", "")
            ?.takeIf { it.isNotBlank() }
            ?: row.id()

        return ClubNews(
            id = id,
            author = author,
            authorImageUrl = avatar?.absUrl("src")?.takeIf { it.isNotBlank() }
                ?: avatar?.attr("src") ?: "",
            message = message,
            dateLabel = dateLabel,
            timestamp = timestamp,
            url = messageLink?.absUrl("href") ?: ""
        )
    }

    override fun parseCredits(doc: Document): List<CreditItem> {
        val table = doc.selectFirst("table.credits, [data-cy=\"creditsContainer\"]") ?: return emptyList()
        return table.select("tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            val name = row.selectFirst("[data-cy=\"creditItemName\"], [data-cy=\"creditItemNameMobile\"]")
                ?.text()?.trim()
                ?: cells.getOrNull(0)?.text()?.trim()
                ?: ""
            val amount = row.selectFirst("[data-cy=\"creditItemAmount\"], [data-cy=\"creditItemAmountMobile\"]")
                ?.text()?.trim()
                ?: cells.getOrNull(1)?.text()?.trim()
                ?: ""
            // Virtuagym renders a "n/a" service row without a usable label;
            // it is not a credit the user can inspect or spend.
            if (name.isBlank() && amount.isBlank()) return@mapNotNull null
            if (isNotAvailable(name) || isNotAvailable(amount)) return@mapNotNull null

            CreditItem(
                name = name,
                amount = amount,
                detailUrl = row.selectFirst("a[href]")?.absUrl("href") ?: "",
                count = CREDIT_COUNT.find(amount)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                isUnlimited = amount.contains("Onbeperkt", ignoreCase = true) ||
                    amount.contains("Unlimited", ignoreCase = true)
            )
        }
    }

    private fun isNotAvailable(value: String): Boolean =
        value.trim().equals("n/a", ignoreCase = true)

    private fun parseDate(value: String): Long = try {
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)
            .parse(value.trim())?.time ?: 0L
    } catch (_: Exception) {
        0L
    }

    private companion object {
        const val LINE_BREAK = "[[br]]"
        val CREDIT_COUNT = Regex("""(\d+)\s*Credits?""", RegexOption.IGNORE_CASE)
        val LAST_RESULT_FLAG = Regex(
            """is_last_result_queue\[\s*["']activitystream["']\s*\]\s*=\s*(-?\d+)"""
        )
    }
}
