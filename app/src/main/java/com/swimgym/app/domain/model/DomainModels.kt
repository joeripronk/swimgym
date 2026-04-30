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
    val classTime: String = "",
    val classDate: String = "",
    val imageUrl: String = "",
    val description: String = "",
    val cost: String = "",
    val totalSpots: Int = 0,
    val cancelPolicy: String = ""
)

data class Booking(
    val id: Int,
    val trainingId: String,
    val userId: Int,
    val status: BookingStatus
)

enum class BookingStatus {
    CONFIRMED,
    CANCELLED,
    PENDING
}