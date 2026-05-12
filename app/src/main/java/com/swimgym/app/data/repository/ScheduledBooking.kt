package com.swimgym.app.data.repository

enum class ScheduledBookingStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    INVALIDATED
}

data class ScheduledBooking(
    val id: Long,
    val trainingId: String,
    val className: String,
    var startTime: Long,
    val instructor: String = "",
    val status: ScheduledBookingStatus = ScheduledBookingStatus.ACTIVE,
    val bookedCount: Int = 0,
    val maxRepeatCount: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)
