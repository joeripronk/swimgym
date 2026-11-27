package com.swimgym.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.swimgym.app.data.local.entity.BookingEntity
import com.swimgym.app.data.local.entity.InstructorEntity
import com.swimgym.app.data.local.entity.TrainingEntity
@Database(
    entities = [TrainingEntity::class, BookingEntity::class, InstructorEntity::class],
    version = 12,
    exportSchema = false
)
abstract class SwimGymDatabase : RoomDatabase() {
    abstract fun dao(): SwimGymDao

    companion object {
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trainings ADD COLUMN eventType INTEGER NOT NULL DEFAULT 8")
            }
        }

        @Volatile
        private var INSTANCE: SwimGymDatabase? = null

        fun getDatabase(context: Context): SwimGymDao {
            return INSTANCE?.let {
                return it.dao()
            } ?: synchronized(this) {
                INSTANCE?.let { return it.dao() }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SwimGymDatabase::class.java,
                    "swimgym_database"
                )
                    .addMigrations(MIGRATION_11_12)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance.dao()
            }
        }
    }
}
