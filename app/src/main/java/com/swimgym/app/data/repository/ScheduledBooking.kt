package com.swimgym.app.data.repository

enum class ScheduledBookingStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    INVALIDATED,
    STOPPED
}

data class ScheduledBooking(
    val id: Long,
    val trainingId: String,
    val className: String,
    val startTime: Long,
    val instructor: String = "",
    val status: ScheduledBookingStatus = ScheduledBookingStatus.ACTIVE,
    val bookedCount: Int = 0,
    val maxRepeatCount: Int? = null,
    val retryCount: Int = 0,
    val lastFailureReason: RetryFailureReason? = null,
    val createdAt: Long = System.currentTimeMillis()
)
