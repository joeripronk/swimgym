package com.swimgym.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.swimgym.app.data.local.entity.BookingEntity
import com.swimgym.app.data.local.entity.InstructorEntity
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.local.entity.UserEntity
import com.swimgym.app.data.local.entity.CacheControlEntity

@Database(
    entities = [TrainingEntity::class, BookingEntity::class, UserEntity::class, InstructorEntity::class, CacheControlEntity::class],
    version = 6,
    exportSchema = false
)
abstract class SwimGymDatabase : RoomDatabase() {
    abstract fun dao(): SwimGymDao
}
