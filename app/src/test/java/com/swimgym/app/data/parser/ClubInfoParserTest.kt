package com.swimgym.app.data.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ClubInfoParserTest {

    private val parser = ClubInfoParserImpl()

    @Test
    fun `parses activity stream messages and skips placeholder rows`() {
        val html = """
            <div id="activitystream" class="ui-activity-stream">
              <div class="activity-stream-row" id="100-activity">
                <div class="activity-stream-avatar">
                  <div class="ui-profile-pic activity-stream" title="Gabriel">
                    <a href="/user/gabriel"><img src="/img/gabriel.png"></a>
                  </div>
                </div>
                <div class="activity-stream-content">
                  <div class="activity-stream-text">
                    <a href="/user/gabriel">Gabriel</a> heeft een
                    <a href="/msgid/18619720">groepsbericht</a> geplaatst in de groep
                    <a href="/groupid/8794">SwimGym NEWS</a>
                  </div>
                  <div class="activity-stream-date">
                    <span title="07-11-2026 12:58">1 weken geleden</span>
                  </div>
                  <div class="activity-stream-quote">"Line one<br><br>Line two"</div>
                </div>
              </div>
              <div class="activity-stream-row" id="101-activity"></div>
            </div>
        """.trimIndent()

        val news = parser.parseNews(Jsoup.parse(html, "https://swimgym.virtuagym.com/groupid/8794"))

        assertEquals(1, news.size)
        assertEquals("18619720", news[0].id)
        assertEquals("Gabriel", news[0].author)
        assertEquals("Line one\n\nLine two", news[0].message)
        assertEquals("1 weken geleden", news[0].dateLabel)
        assertTrue(news[0].timestamp > 0)
        assertEquals("https://swimgym.virtuagym.com/msgid/18619720", news[0].url)
        assertEquals("https://swimgym.virtuagym.com/img/gabriel.png", news[0].authorImageUrl)
    }

    @Test
    fun `parses full group messages with permalink and image`() {
        val html = """
            <div id="group_messages">
              <div class="ui-message" id="message_42">
                <div class="message">
                  <div class="header">
                    <div class="user-image">
                      <div class="ui-profile-pic group-message" title="Gabriel">
                        <a href="/user/gabriel"><img src="/img/avatar.png"></a>
                      </div>
                    </div>
                    <div class="user-details">
                      <div class="user-name">
                        <a href="/user/gabriel">Gabriel</a>
                        <span class="staff-badge">Medewerker</span>
                      </div>
                      <div class="message-date">
                        <a href="/group/swimgym-portal/some-news" title="07-11-2026 12:58">1 weken geleden</a>
                      </div>
                    </div>
                  </div>
                  <div class="content"><p>First line</p><p>Second line</p></div>
                  <div class="media"><a href="/img/news.jpg"><div class="grid_image"></div></a></div>
                </div>
              </div>
            </div>
        """.trimIndent()

        val news = parser.parseNews(
            Jsoup.parse(html, "https://swimgym.virtuagym.com/groupid/8794")
        )

        assertEquals(1, news.size)
        assertEquals("42", news[0].id)
        assertEquals("Gabriel", news[0].author)
        assertEquals("First line\nSecond line", news[0].message)
        assertEquals("https://swimgym.virtuagym.com/group/swimgym-portal/some-news", news[0].url)
        assertEquals("https://swimgym.virtuagym.com/img/news.jpg", news[0].imageUrl)
        assertTrue(news[0].timestamp > 0)
    }

    @Test
    fun `parses desktop and mobile credit cells`() {
        val html = """
            <table class="credits" data-cy="creditsContainer">
              <thead><tr><th>Dienst</th><th>Aantal</th></tr></thead>
              <tbody>
                <tr>
                  <td><a data-cy="creditItemName" href="/user/x/credits/access">SwimGym</a></td>
                  <td><a data-cy="creditItemAmount" href="/user/x/credits/access">0 Credits</a></td>
                </tr>
                <tr>
                  <td><a data-cy="creditItemName" href="/user/x/credits/entree">n/a</a></td>
                  <td><a data-cy="creditItemAmount" href="/user/x/credits/entree">Onbeperkt</a></td>
                </tr>
                <tr>
                  <td><span data-cy="creditItemNameMobile">Dryland</span></td>
                  <td><span data-cy="creditItemAmountMobile">1 Credits</span></td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()

        val credits = parser.parseCredits(
            Jsoup.parse(html, "https://swimgym.virtuagym.com/user/x/myservices")
        )

        assertEquals(3, credits.size)
        assertEquals("SwimGym", credits[0].name)
        assertEquals(0, credits[0].count)
        assertEquals("https://swimgym.virtuagym.com/user/x/credits/access", credits[0].detailUrl)
        assertTrue(credits[1].isUnlimited)
        assertEquals("Dryland", credits[2].name)
        assertEquals(1, credits[2].count)
    }

    @Test
    fun `parses the saved SwimGym NEWS page`() {
        val page = savedPage("SwimGym Online Fitness _ SwimGym NEWS.htm") ?: return
        val news = parser.parseNews(
            Jsoup.parse(page.readText(), "https://swimgym.virtuagym.com/groupid/8794")
        )

        assertEquals(5, news.size)
        assertTrue(news.all { it.author.isNotBlank() && it.message.isNotBlank() })
        assertTrue(news.all { it.timestamp > 0 })
        assertTrue(news.all { !it.message.endsWith("...") })
        assertEquals("18619720", news.first().id)
        assertEquals("Gabriel", news.first().author)
        assertEquals(
            "https://swimgym.virtuagym.com/group/swimgym-portal/another-project-complete-f",
            news.first().url
        )
        assertTrue(news.first().imageUrl.isNotBlank())
    }

    @Test
    fun `parses the saved club account credits table`() {
        val page = savedPage("SwimGym Online Fitness _ Clubaccount informatie.htm") ?: return
        val credits = parser.parseCredits(
            Jsoup.parse(page.readText(), "https://swimgym.virtuagym.com/user/swimgymfinv-4b03c25e/myservices")
        )

        assertEquals(6, credits.size)
        assertTrue(credits.any { it.isUnlimited })
        assertTrue(credits.any { it.count == 1 })
    }

    private fun savedPage(name: String): File? {
        var dir = File(".").absoluteFile
        repeat(5) {
            val candidate = File(dir, "tools/pages/$name")
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return null
        }
        return null
    }
}
