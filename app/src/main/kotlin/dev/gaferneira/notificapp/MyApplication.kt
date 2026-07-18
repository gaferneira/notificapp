package dev.gaferneira.notificapp

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy.Builder
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import dev.gaferneira.notificapp.core.notification.action.WebhookRetrySweepWorker
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * [Application] class for MyApplication
 */
@HiltAndroidApp
class MyApplication :
    Application(),
    ImageLoaderFactory,
    Configuration.Provider {

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * On-demand WorkManager init (see the manifest's removed default `WorkManagerInitializer`),
     * wired with [HiltWorkerFactory] so `@HiltWorker` workers (webhook delivery, Phase 4 PR2)
     * get constructor injection.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()

        setStrictModePolicy()

        if (hasEnqueuedRetrySweepThisProcess.compareAndSet(false, true)) {
            WorkManager.getInstance(this).enqueueUniqueWork(
                WebhookRetrySweepWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WebhookRetrySweepWorker>().build(),
            )
        }
    }

    override fun newImageLoader(): ImageLoader = imageLoader.get()

    /**
     * Return true if the application is debuggable.
     */
    private fun isDebuggable(): Boolean = 0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE

    /**
     * Set a thread policy that detects all potential problems on the main thread, such as network
     * and disk access.
     *
     * If a problem is found, the offending call will be logged and the application will be killed.
     */
    private fun setStrictModePolicy() {
        if (isDebuggable()) {
            StrictMode.setThreadPolicy(
                Builder().detectAll().penaltyLog().build(),
            )
        }
    }

    private companion object {
        val hasEnqueuedRetrySweepThisProcess = AtomicBoolean(false)
    }
}
