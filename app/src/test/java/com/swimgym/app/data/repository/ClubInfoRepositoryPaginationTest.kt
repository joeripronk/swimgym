package com.swimgym.app.data.repository

import com.swimgym.app.data.api.VirtuagymApiClient
import com.swimgym.app.data.parser.ClubInfoParser
import com.swimgym.app.domain.model.ClubNews
import com.swimgym.app.domain.model.ClubNewsPage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClubInfoRepositoryPaginationTest {

    private val api = mockk<VirtuagymApiClient>()
    private val parser = mockk<ClubInfoParser>()

    @Test
    fun `getNewsPage fetches the requested activity stream page`() = runTest {
        val url = "https://swimgym.virtuagym.com/group/swimgym-portal/ajax" +
            "?action=get_activitystream&page=2"
        val doc = mockk<Document>()
        val news = ClubNews(id = "42", author = "Gabriel", message = "More news")
        coEvery { api.fetchHtml(url) } returns doc
        every { parser.parseNews(doc) } returns listOf(news)
        every { parser.isLastNewsPage(doc) } returns false

        val result = ClubInfoRepositoryImpl(api, parser).getNewsPage(2)

        assertTrue(result.isSuccess)
        assertEquals(ClubNewsPage(listOf(news), isLast = false), result.getOrNull())
        coVerify(exactly = 1) { api.fetchHtml(url) }
    }

    @Test
    fun `getNewsPage reports failures instead of silently returning empty news`() = runTest {
        val url = "https://swimgym.virtuagym.com/group/swimgym-portal/ajax" +
            "?action=get_activitystream&page=3"
        coEvery { api.fetchHtml(url) } throws Exception("network down")

        val result = ClubInfoRepositoryImpl(api, parser).getNewsPage(3)

        assertTrue(result.isFailure)
    }
}
