package com.swimgym.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.swimgym.app.data.local.entity.BookingEntity
import com.swimgym.app.data.local.entity.InstructorEntity
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.local.entity.CacheControlEntity

@Database(
    entities = [TrainingEntity::class, BookingEntity::class, InstructorEntity::class, CacheControlEntity::class],
    version = 9,
    exportSchema = false
)
abstract class SwimGymDatabase : RoomDatabase() {
    abstract fun dao(): SwimGymDao
}

fun Context.database(): SwimGymDao {
    return Room.databaseBuilder(this, SwimGymDatabase::class.java, "swimgym_database").build().dao()
}
