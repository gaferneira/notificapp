package dev.gaferneira.notificapp.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleDao
import dev.gaferneira.notificapp.core.data.local.dao.SelectedAppDao
import javax.inject.Singleton

/**
 * Dagger module for providing database dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the Room database instance.
     *
     * @param context Application context
     * @return AppDatabase instance
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "notificapp_database",
    )
        .build()

    /**
     * Provides the SelectedAppDao.
     *
     * @param database AppDatabase instance
     * @return SelectedAppDao
     */
    @Provides
    fun provideSelectedAppDao(database: AppDatabase): SelectedAppDao = database.selectedAppDao()

    /**
     * Provides the NotificationDao.
     *
     * @param database AppDatabase instance
     * @return NotificationDao
     */
    @Provides
    fun provideNotificationDao(database: AppDatabase): NotificationDao = database.notificationDao()

    /**
     * Provides the RuleDao.
     *
     * @param database AppDatabase instance
     * @return RuleDao
     */
    @Provides
    fun provideRuleDao(database: AppDatabase): RuleDao = database.ruleDao()
}
