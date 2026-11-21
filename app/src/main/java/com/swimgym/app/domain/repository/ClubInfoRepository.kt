package com.swimgym.app.domain.repository

import com.swimgym.app.domain.model.ClubInfo
import com.swimgym.app.domain.model.ClubNewsPage

interface ClubInfoRepository {
    suspend fun getClubInfo(): Result<ClubInfo>
    suspend fun getNewsPage(page: Int): Result<ClubNewsPage>
}
