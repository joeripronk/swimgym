package com.swimgym.app.data.model

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String
)

data class TrainingDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("instructor") val instructor: String,
    @SerializedName("start_time") val startTime: Long,
    @SerializedName("end_time") val endTime: Long,
    @SerializedName("location") val location: String,
    @SerializedName("spots_available") val spotsAvailable: Int,
    var isJoined: Boolean = false,
    var classTime: String = "",
    var classDate: String = "",
    var imageUrl: String = "",
    var description: String = "",
    var cost: String = "",
    var totalSpots: Int = 0,
    var cancelPolicy: String = ""
)

data class BookingResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("training_id") val trainingId: String,
    @SerializedName("user_id") val userId: Int,
    @SerializedName("status") val status: String,
    val className: String = "",
    val classTime: String = "",
    val classDate: String = ""
)