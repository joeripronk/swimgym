package com.swimgym.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache_control")
data class CacheControlEntity(
    @PrimaryKey
    val id: Int = 1,
    val lastSyncTime: Long = 0L,
    val nextSyncTime: Long = 0L,
    val syncIntervalMillis: Long = 30 * 60 * 1000L,
    val staleAfterMillis: Long = 1 * 60 * 1000L
)
