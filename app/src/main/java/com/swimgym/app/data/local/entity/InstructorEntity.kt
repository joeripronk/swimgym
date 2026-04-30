package com.swimgym.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "instructors")
data class InstructorEntity(
    @PrimaryKey
    val instructorName: String,
    val instructorLink: String,
    val instructorImage: String
)
