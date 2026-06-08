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

    companion object {
        @Volatile
        private var INSTANCE: SwimGymDatabase? = null

        fun getDatabase(context: Context): SwimGymDao {
            return INSTANCE?.let {
                return it.dao()
            } ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SwimGymDatabase::class.java,
                    "swimgym_database"
                ).build()
                INSTANCE = instance
                instance.dao()
            }
        }
    }
}
