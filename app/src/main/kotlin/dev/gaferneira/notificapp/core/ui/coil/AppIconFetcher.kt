package dev.gaferneira.notificapp.core.ui.coil

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options

/** Coil request model for the launcher icon of an installed app. */
data class AppIconData(val packageName: String)

/** Memory-cache key so each package icon is decoded once and shared across rows/screens. */
class AppIconKeyer : Keyer<AppIconData> {
    override fun key(data: AppIconData, options: Options): String = "app-icon:${data.packageName}"
}

/**
 * Loads an installed app's icon through [android.content.pm.PackageManager] on Coil's fetch
 * dispatcher (off the main thread). Throws [android.content.pm.PackageManager.NameNotFoundException]
 * when the package is gone, which Coil surfaces as an error result for the UI to fall back on.
 */
class AppIconFetcher(
    private val data: AppIconData,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult = DrawableResult(
        drawable = context.packageManager.getApplicationIcon(data.packageName),
        isSampled = false,
        dataSource = DataSource.DISK,
    )

    class Factory(private val context: Context) : Fetcher.Factory<AppIconData> {
        override fun create(data: AppIconData, options: Options, imageLoader: ImageLoader): Fetcher = AppIconFetcher(data, context)
    }
}
