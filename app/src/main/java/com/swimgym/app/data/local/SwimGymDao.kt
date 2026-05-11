package com.swimgym.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.swimgym.app.data.local.entity.BookingEntity
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.data.local.entity.InstructorEntity
import com.swimgym.app.data.local.entity.CacheControlEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SwimGymDao {

    @Query("SELECT * FROM trainings ORDER BY startTime ASC")
    fun getAllTrainings(): Flow<List<TrainingEntity>>

    @Query("SELECT * FROM trainings ORDER BY startTime ASC")
    suspend fun getAllTrainingsList(): List<TrainingEntity>

    @Query("SELECT * FROM trainings WHERE isJoined = 1 ORDER BY startTime ASC")
    fun getConfirmedBookings(): Flow<List<TrainingEntity>>

    @Query("SELECT * FROM trainings WHERE id = :trainingId")
    suspend fun getTrainingById(trainingId: String): TrainingEntity?

    @Query("SELECT * FROM trainings WHERE startTime = :startTime")
    suspend fun getTrainingByStartTime(startTime: Long): TrainingEntity?

    @Query("SELECT * FROM trainings WHERE startTime >= :startTime ORDER BY startTime ASC")
    suspend fun getTrainingsFrom(startTime: Long): List<TrainingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrainings(trainings: List<TrainingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTraining(training: TrainingEntity)

    @Query("DELETE FROM trainings")
    suspend fun clearTrainings()

    @Query("DELETE FROM bookings")
    suspend fun clearBookings()

    @Query("SELECT * FROM bookings ORDER BY lastUpdated DESC")
    fun getAllBookings(): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE trainingId = :trainingId")
    suspend fun getBookingByTrainingId(trainingId: String): BookingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: BookingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookings(bookings: List<BookingEntity>)

    @Delete
    suspend fun deleteBooking(booking: BookingEntity)

    @Query("DELETE FROM bookings WHERE trainingId = :trainingId")
    suspend fun deleteBookingByTrainingId(trainingId: String)

    @Query("DELETE FROM trainings WHERE startTime < :beforeTime")
    suspend fun deleteOldTrainings(beforeTime: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstructor(instructor: InstructorEntity)

    @Query("SELECT * FROM instructors WHERE instructorName = :name")
    suspend fun getInstructor(name: String): InstructorEntity?

    @Query("SELECT * FROM instructors")
    suspend fun getAllInstructors(): List<InstructorEntity>

    @Query("SELECT * FROM cache_control WHERE id = 1")
    suspend fun getCacheControl(): CacheControlEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCacheControl(cacheControl: CacheControlEntity)

    @Query("DELETE FROM cache_control")
    suspend fun clearCacheControl()
}
