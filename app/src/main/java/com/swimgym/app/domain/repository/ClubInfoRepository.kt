package com.swimgym.app.domain.repository

import com.swimgym.app.domain.model.ClubInfo
import com.swimgym.app.domain.model.ClubNews
import com.swimgym.app.domain.model.ClubNewsPage

interface ClubInfoRepository {
    suspend fun getClubInfo(): Result<ClubInfo>
    suspend fun getNewsPage(page: Int): Result<ClubNewsPage>
    suspend fun getNewsDetail(newsId: String): Result<ClubNews?>
}
