package com.swimgym.app.data.repository

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.swimgym.app.data.local.SwimGymDao

class TrainerImageCache(
    private val context: Context,
    private val dao: SwimGymDao
) {
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    fun resolveTrainerImageUrl(instructorName: String, fallbackUrl: String): String = fallbackUrl

    suspend fun getInstructorImage(instructorName: String): String? {
        return dao.getInstructor(instructorName)?.instructorImage
    }
}