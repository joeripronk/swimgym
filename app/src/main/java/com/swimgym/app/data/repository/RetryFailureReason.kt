package com.swimgym.app.data.repository

import java.io.IOException
import java.util.Locale

/**
 * Why an automatic (recurring) booking attempt failed. Stored on the scheduled
 * booking so the retry can be explained in the notification and in My Bookings.
 */
enum class RetryFailureReason {
    CLASS_FULL,
    CONNECTION_ERROR,
    AUTH_FAILED,
    SERVICE_DOWN,
    UNKNOWN;

    val displayName: String
        get() = when (this) {
            CLASS_FULL -> "class full"
            CONNECTION_ERROR -> "connection error"
            AUTH_FAILED -> "auth failed"
            SERVICE_DOWN -> "service down"
            UNKNOWN -> "unknown error"
        }

    companion object {
        fun from(error: Throwable?): RetryFailureReason {
            if (error == null) return UNKNOWN
            if (error is IOException) return CONNECTION_ERROR
            val message = error.message?.lowercase(Locale.ROOT) ?: return UNKNOWN
            return when {
                message.contains("not logged in") ||
                    message.contains("need to login") ||
                    message.contains("logged out") -> AUTH_FAILED

                message.contains("not confirmed") ||
                    message.contains("class full") -> CLASS_FULL

                message.contains("failed to fetch data") ||
                    message.contains("booking failed: 5") ||
                    message.contains("cancel failed: 5") ||
                    message.contains("service unavailable") -> SERVICE_DOWN

                else -> UNKNOWN
            }
        }

        fun fromName(name: String?): RetryFailureReason? = try {
            name?.let { valueOf(it) }
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
