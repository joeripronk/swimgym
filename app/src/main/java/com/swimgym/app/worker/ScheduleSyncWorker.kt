package com.swimgym.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swimgym.app.data.api.WebScraper
import com.swimgym.app.data.local.SwimGymDao
import com.swimgym.app.data.local.entity.CacheControlEntity
import com.swimgym.app.data.model.Mappers.toEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ScheduleSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val webScraper: WebScraper,
    private val dao: SwimGymDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            syncSchedule()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun syncSchedule() {
        val now = System.currentTimeMillis()
        
        dao.deleteOldTrainings(now)
/*
        val result = webScraper.getSchedule(
            weeks = 1
        )

        result.fold(
            onSuccess = { trainings ->
                val futureTrainings = trainings.filter { it.startTime >= now }
                dao.insertTrainings(futureTrainings.map { it.toEntity() })
                dao.insertCacheControl(
                    CacheControlEntity(
                        id = 1,
                        lastSyncTime = System.currentTimeMillis(),
                        nextSyncTime = System.currentTimeMillis() + 30 * 60 * 1000L,
                        syncIntervalMillis = 30 * 60 * 1000L,
                        staleAfterMillis = 1440 * 60 * 1000L
                    )
                )
            },
            onFailure = {
                // Sync failed, keep existing data
            }
        )
  */
    }

    companion object {
        const val WORK_NAME = "schedule_sync"
    }
}
