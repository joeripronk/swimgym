package com.swimgym.app.data.parser

import com.swimgym.app.domain.model.ClubNews
import com.swimgym.app.domain.model.CreditItem
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

interface ClubInfoParser {
    fun parseNews(doc: Document): List<ClubNews>
    fun parseCredits(doc: Document): List<CreditItem>
}

/**
 * Parses the two Virtuagym pages captured in tools/pages/:
 *  - SwimGym NEWS is a group activity stream (`.activity-stream-row`)
 *  - Credits live in the club account "Credits" table (`table.credits`)
 *
 * Both parsers skip empty/placeholder rows and degrade gracefully when the
 * portal changes its markup, so one broken section does not hide the other.
 */
class ClubInfoParserImpl : ClubInfoParser {

    override fun parseNews(doc: Document): List<ClubNews> =
        doc.select(".activity-stream-row").mapNotNull(::parseNewsRow)

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
            if (name.isBlank() && amount.isBlank()) return@mapNotNull null

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

    private fun parseDate(value: String): Long = try {
        SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)
            .parse(value.trim())?.time ?: 0L
    } catch (_: Exception) {
        0L
    }

    private companion object {
        const val LINE_BREAK = "[[br]]"
        val CREDIT_COUNT = Regex("""(\d+)\s*Credits?""", RegexOption.IGNORE_CASE)
    }
}
