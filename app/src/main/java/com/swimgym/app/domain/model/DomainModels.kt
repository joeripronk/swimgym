package com.swimgym.app.domain.model

data class User(
    val id: Int,
    val name: String,
    val email: String
)

data class Training(
    val id: String,
    val title: String,
    val instructor: String,
    val instructorLink: String = "",
    val startTime: Long,
    val endTime: Long,
    val location: String,
    val spotsAvailable: Int,
    val isJoined: Boolean = false,
    val isFull: Boolean = false,
    val classTime: String = "",
    val classDate: String = "",
    var imageUrl: String = "",
    val description: String = "",
    val cost: String = "",
    val totalSpots: Int = 0,
    val cancelPolicy: String = "",
    val eventId: String = "",
    val calendarId: String = ""
)

data class Booking(
    val id: Long,
    val trainingId: String,
    val status: BookingStatus,
    val className: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val instructor: String = "",
    val imageUrl: String = ""
)

enum class BookingStatus {
    CONFIRMED,
    CANCELLED,
    PENDING,
    IN_PROGRESS,
    BOOKED,
    FAILED
}

data class ReminderConfig(
    val reminderMinutesBefore: Int = 15,
    val reminderEnabled: Boolean = true
)

data class CalendarIntegrationConfig(
    val reminderConfig: ReminderConfig = ReminderConfig(),
    val calendarName: String = "Default Calendar"
)

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val ownerAccount: String,
    val isPrimary: Boolean
)

/** A single message from the SwimGym NEWS group activity stream. */
data class ClubNews(
    val id: String,
    val author: String,
    val authorImageUrl: String = "",
    val message: String,
    val dateLabel: String = "",
    val timestamp: Long = 0L,
    val url: String = "",
    val imageUrl: String = ""
)

/** One row of the "Credits" table on the club account information page. */
data class CreditItem(
    val name: String,
    val amount: String,
    val detailUrl: String = "",
    val count: Int? = null,
    val isUnlimited: Boolean = false
)

data class ClubInfo(
    val news: List<ClubNews> = emptyList(),
    val credits: List<CreditItem> = emptyList()
)