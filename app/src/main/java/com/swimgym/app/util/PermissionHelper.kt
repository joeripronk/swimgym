package com.swimgym.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionHelper {
    
    /**
     * Checks if notification permission is granted
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for Android 12 and below
        }
    }
    
    /**
     * Checks if calendar read permission is granted
     */
    fun hasCalendarReadPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Checks if calendar write permission is granted
     */
    fun hasCalendarWritePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Returns a list of permissions that need to be requested
     */
    fun getRequiredPermissions(context: Context): List<String> {
        val permissions = mutableListOf<String>()
        
        if (!hasNotificationPermission(context)) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (!hasCalendarReadPermission(context)) {
            permissions.add(Manifest.permission.READ_CALENDAR)
        }
        
        if (!hasCalendarWritePermission(context)) {
            permissions.add(Manifest.permission.WRITE_CALENDAR)
        }
        
        return permissions
    }
}
