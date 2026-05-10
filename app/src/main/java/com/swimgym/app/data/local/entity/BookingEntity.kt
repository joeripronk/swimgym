package com.swimgym.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trainingId: String,
    val userId: Int,
    val startTime: Long,
    val endTime: Long = 0L,
    val title: String,
    val status: String,
    val lastUpdated: Long = System.currentTimeMillis()
)