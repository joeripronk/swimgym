package com.swimgym.app.data.repository

import java.util.*

enum class ScheduledBookingStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    INVALIDATED
}

data class ScheduledBooking(
    val id: String,
    val trainingId: String,
    val className: String,
    val classTime: String,
    val classDate: String,
    val startTime: Long = 0L,
    val instructor: String = "",
    val status: ScheduledBookingStatus = ScheduledBookingStatus.ACTIVE,
    val bookedCount: Int = 0,
    val maxRepeatCount: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)
