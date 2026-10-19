package com.swimgym.app.util

import java.util.Calendar

fun addDaysToSeconds(seconds: Long, days: Int): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = seconds * 1000
    cal.add(Calendar.DAY_OF_MONTH, days)
    return cal.timeInMillis / 1000
}
