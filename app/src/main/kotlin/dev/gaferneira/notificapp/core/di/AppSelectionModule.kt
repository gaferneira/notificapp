package dev.gaferneira.notificapp.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.gaferneira.notificapp.features.appselection.data.InstalledAppsProvider
import dev.gaferneira.notificapp.features.appselection.data.PackageManagerInstalledAppsProvider

/**
 * Dagger module for binding the app-selection feature's Android-facing seams.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppSelectionModule {

    /**
     * Binds InstalledAppsProvider interface to its PackageManager-backed implementation.
     */
    @Binds
    abstract fun bindInstalledAppsProvider(impl: PackageManagerInstalledAppsProvider): InstalledAppsProvider
}
