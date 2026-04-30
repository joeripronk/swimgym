package com.swimgym.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swimgym.app.data.api.WebScraper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.*

@HiltWorker
class RecurringBookingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val webScraper: WebScraper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bookingId = inputData.getString(KEY_BOOKING_ID) ?: return Result.failure()
        val trainingId = inputData.getString(KEY_TRAINING_ID) ?: return Result.failure()
        val className = inputData.getString(KEY_CLASS_NAME) ?: return Result.failure()
        val classTime = inputData.getString(KEY_CLASS_TIME) ?: ""
        val classDate = inputData.getString(KEY_CLASS_DATE) ?: ""
        val startTime = inputData.getLong(KEY_START_TIME, 0L)
        val instructor = inputData.getString(KEY_INSTRUCTOR) ?: ""
        val maxRepeatCount = inputData.getInt(KEY_MAX_REPEAT, -1)
        val currentCount = inputData.getInt(KEY_CURRENT_COUNT, 0)

        return try {
            // Book the training
            val result = webScraper.bookTraining(trainingId, className, classTime, classDate)

            result.fold(
                onSuccess = { bookingResponse ->
                    // Schedule next week if needed
                    if (shouldContinue(currentCount, maxRepeatCount)) {
                        scheduleNextWeek(
                            bookingId, trainingId, className, classTime, classDate,
                            startTime, instructor, maxRepeatCount, currentCount + 1
                        )
                    }
                    Result.success()
                },
                onFailure = {
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun shouldContinue(currentCount: Int, maxRepeat: Int): Boolean {
        if (maxRepeat == -1) return true
        return currentCount < maxRepeat
    }

    private fun scheduleNextWeek(
        bookingId: String,
        trainingId: String,
        className: String,
        classTime: String,
        classDate: String,
        startTime: Long = 0L,
        instructor: String = "",
        maxRepeatCount: Int,
        currentCount: Int
    ) {
        // Calculate delay: 7 days from now
        val delay = 7 * 24 * 60 * 60 * 1000L

        val workRequest = androidx.work.OneTimeWorkRequest.Builder(
            RecurringBookingWorker::class.java
        )
            .setInputData(
                androidx.work.Data.Builder()
                    .putString(KEY_BOOKING_ID, bookingId)
                    .putString(KEY_TRAINING_ID, trainingId)
                    .putString(KEY_CLASS_NAME, className)
                    .putString(KEY_CLASS_TIME, classTime)
                    .putString(KEY_CLASS_DATE, classDate)
                    .putLong(KEY_START_TIME, startTime)
                    .putString(KEY_INSTRUCTOR, instructor)
                    .putInt(KEY_MAX_REPEAT, maxRepeatCount)
                    .putInt(KEY_CURRENT_COUNT, currentCount)
                    .build()
            )
            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "recurring_booking_$bookingId",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    companion object {
        const val KEY_BOOKING_ID = "booking_id"
        const val KEY_TRAINING_ID = "training_id"
        const val KEY_CLASS_NAME = "class_name"
        const val KEY_CLASS_TIME = "class_time"
        const val KEY_CLASS_DATE = "class_date"
        const val KEY_START_TIME = "start_time"
        const val KEY_INSTRUCTOR = "instructor"
        const val KEY_MAX_REPEAT = "max_repeat"
        const val KEY_CURRENT_COUNT = "current_count"
    }
}
