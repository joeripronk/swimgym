package com.swimgym.app.data.model

import com.swimgym.app.data.local.entity.BookingEntity
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.domain.model.*

object Mappers {
    //fun UserDto.toDomain() = User(id, name, email)
   // fun User.toEntity() = UserEntity(id, name, email)
    //fun UserEntity.toDomain() = User(id, name, email)

    fun TrainingDto.toDomain(): Training {
        return Training(
            id = id,
            title = title,
            instructor = instructor,
            instructorLink = "",
            startTime = startTime,
            endTime = endTime,
            location = location,
            spotsAvailable = spotsAvailable,
            isJoined = isJoined,
            isFull = isFull,
            classTime = classTime,
            classDate = classDate,
            imageUrl = imageUrl,
            description = description,
            cost = cost,
            totalSpots = totalSpots,
            cancelPolicy = cancelPolicy
        )
    }

    fun TrainingDto.toEntity() = TrainingEntity(
        id = id,
        title = title,
        instructor = instructor,
        instructorLink = "",
        startTime = startTime,
        endTime = endTime,
        location = location,
        spotsAvailable = spotsAvailable,
        isJoined = isJoined,
        isFull = isFull,
        classTime = classTime,
        classDate = classDate,
        imageUrl = imageUrl,
        description = description,
        cost = cost,
        totalSpots = totalSpots,
        cancelPolicy = cancelPolicy
    )

    fun Training.toEntity() = TrainingEntity(
        id = id,
        title = title,
        instructor = instructor,
        instructorLink = instructorLink,
        startTime = startTime,
        endTime = endTime,
        location = location,
        spotsAvailable = spotsAvailable,
        isJoined = isJoined,
        isFull = isFull,
        classTime = classTime,
        classDate = classDate,
        imageUrl = imageUrl,
        description = description,
        cost = cost,
        totalSpots = totalSpots,
        cancelPolicy = cancelPolicy
    )

    fun TrainingEntity.toDomain() = Training(
        id = id,
        title = title,
        instructor = instructor,
        instructorLink = instructorLink,
        startTime = startTime,
        endTime = endTime,
        location = location,
        spotsAvailable = spotsAvailable,
        isJoined = isJoined,
        isFull = isFull,
        classTime = classTime,
        classDate = classDate,
        imageUrl = imageUrl,
        description = description,
        cost = cost,
        totalSpots = totalSpots,
        cancelPolicy = cancelPolicy
    )

    fun BookingResponse.toDomain(): Booking {
        return Booking(
            id = id,
            trainingId = trainingId.toString(),
            status = when (status.lowercase()) {
                "confirmed" -> BookingStatus.CONFIRMED
                "cancelled" -> BookingStatus.CANCELLED
                else -> BookingStatus.PENDING
            },
            title = className,
            classTime = classTime,
            classDate = classDate
        )
    }

    fun Booking.toEntity() = BookingEntity(
        id = id,
        trainingId = trainingId,
        title = title,
        instructor = instructor,
        status = status.name
    )

    fun BookingEntity.toDomain() = Booking(
        id = id,
        trainingId = trainingId,
        title = title,
        instructor = instructor,
        status = when (status) {
            "CONFIRMED" -> BookingStatus.CONFIRMED
            "CANCELLED" -> BookingStatus.CANCELLED
            else -> BookingStatus.PENDING
        }
    )

    private fun parseIsoDate(isoDate: String): Long {
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .parse(isoDate)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}