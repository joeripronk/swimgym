package com.swimgym.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.swimgym.app.data.local.entity.BookingEntity
import com.swimgym.app.data.local.entity.InstructorEntity
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.local.entity.UserEntity

@Database(
    entities = [TrainingEntity::class, BookingEntity::class, UserEntity::class, InstructorEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SwimGymDatabase : RoomDatabase() {
    abstract fun dao(): SwimGymDao
}
