package com.swimgym.app.domain.repository

import com.swimgym.app.domain.model.ClubInfo

interface ClubInfoRepository {
    suspend fun getClubInfo(): Result<ClubInfo>
}
