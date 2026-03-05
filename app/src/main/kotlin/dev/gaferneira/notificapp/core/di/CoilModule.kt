package dev.gaferneira.notificapp.core.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger module for providing Coil ImageLoader dependency.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    /**
     * Provides a singleton [ImageLoader] instance with caching enabled.
     *
     * @param context Application context
     * @return Configured ImageLoader
     */
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("coil_cache"))
                .maxSizeBytes(50L * 1024 * 1024) // 50MB
                .build()
        }
        .crossfade(true)
        .build()
}
