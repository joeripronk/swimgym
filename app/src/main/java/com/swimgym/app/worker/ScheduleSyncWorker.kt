package com.swimgym.app.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.swimgym.app.di.SwimGymAppContainer
import com.swimgym.app.data.local.entity.CacheControlEntity
import com.swimgym.app.data.model.Mappers.toEntity
import kotlinx.coroutines.runBlocking

class ScheduleSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    private val container = SwimGymAppContainer.getInstance()
    private val dao = container.dao

    private val webScraper = container.webScraper
    override fun doWork(): Result {
        return try {
            runBlocking {
                syncSchedule()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun syncSchedule() {
        val now = System.currentTimeMillis()/1000
        
        dao.deleteOldTrainings(now)
        webScraper.getSchedule(applicationContext)
    }

    companion object {
        const val WORK_NAME = "schedule_sync"
    }
}