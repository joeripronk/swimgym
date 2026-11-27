package com.swimgym.app.data.model

import com.swimgym.app.data.local.entity.BookingEntity
import com.swimgym.app.data.local.entity.TrainingEntity
import com.swimgym.app.domain.model.*
import java.util.Locale

object Mappers {


    fun TrainingEntity.toDto(): TrainingDto {
        return TrainingDto(
            id = id,
            eventType = eventType,
            title = title,
            instructor = instructor,
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
            cancelPolicy = cancelPolicy,
            eventId = eventId,
            calendarId = calendarId,
            instructorLink = instructorLink,
            lastFetched = lastFetched
        )
    }

    fun TrainingDto.toEntity() = TrainingEntity(
        id = id,
        eventType = eventType,
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
        cancelPolicy = cancelPolicy,
        eventId = eventId,
        calendarId = calendarId,
        lastFetched = lastFetched
    )

    fun Training.toEntity() = TrainingEntity(
        id = id,
        eventType = eventType,
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
        cancelPolicy = cancelPolicy,
        eventId = eventId,
        calendarId = calendarId
    )

    fun TrainingEntity.toDomain() = Training(
        id = id,
        eventType = eventType,
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
        cancelPolicy = cancelPolicy,
        eventId = eventId,
        calendarId = calendarId
    )

    fun BookingResponse.toDomain(): Booking {
        return Booking(
            id = id,
            trainingId = trainingId,
            status = when (status?.lowercase(Locale.ROOT)) {
                "confirmed" -> BookingStatus.CONFIRMED
                "cancelled" -> BookingStatus.CANCELLED
                else -> BookingStatus.PENDING
            },
            className = className,
            startTime = startTime,
            endTime = endTime
        )
    }

    fun Booking.toEntity() = BookingEntity(
        id = id,
        trainingId = trainingId,
        startTime = startTime,
        endTime = endTime,
        title = className,
        status = status.name
    )

    fun BookingEntity.toDomain() = Booking(
        id = id,
        trainingId = trainingId,
        status = runCatching { BookingStatus.valueOf(status) }
            .getOrDefault(BookingStatus.PENDING),
        className = title,
        startTime = startTime,
        endTime = endTime
    )
}