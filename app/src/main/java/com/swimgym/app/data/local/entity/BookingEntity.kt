package com.swimgym.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookings")
data class BookingEntity(
    //@PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @PrimaryKey
    val trainingId: String,
    val userId: Int,
    val status: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
