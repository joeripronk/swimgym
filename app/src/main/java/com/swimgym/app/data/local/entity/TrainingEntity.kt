package com.swimgym.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trainings")
data class TrainingEntity(
    @PrimaryKey
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
    val cancelPolicy: String = "",
    val lastFetched: Long = System.currentTimeMillis()
)
