package dev.gaferneira.notificapp.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.gaferneira.notificapp.BuildConfig
import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.data.local.dao.DataBrowserDao
import dev.gaferneira.notificapp.core.data.local.dao.ExtractedFieldValueDao
import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleExecutionDao
import dev.gaferneira.notificapp.core.data.local.dao.SelectedAppDao
import dev.gaferneira.notificapp.core.data.local.migration.APP_DATABASE_MIGRATIONS
import dev.gaferneira.notificapp.core.data.local.security.DatabasePassphraseProvider
import dev.gaferneira.notificapp.core.data.local.security.DatabaseRekeyer
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

private const val DATABASE_NAME = "notificapp_database"

/**
 * Dagger module for providing database dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    /**
     * Provides the Room database instance, encrypted at rest with SQLCipher (DATA-02). The
     * passphrase is KeyStore-backed (see [DatabasePassphraseProvider]); pre-DATA-02 plaintext
     * installs are rekeyed in place on first open rather than destroyed.
     *
     * @param context Application context
     * @return AppDatabase instance
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: DatabasePassphraseProvider,
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = passphraseProvider.getOrCreatePassphrase()
        DatabaseRekeyer.rekeyIfNeeded(context, DATABASE_NAME, passphrase)
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            DATABASE_NAME,
        )
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addMigrations(*APP_DATABASE_MIGRATIONS)
            .applyDebugOnlyFallback()
            .build()
    }

    /**
     * Debug builds may skip migrations during development; release builds must crash loudly on a
     * missing migration rather than silently wiping user data (TD-10).
     */
    private fun RoomDatabase.Builder<AppDatabase>.applyDebugOnlyFallback(): RoomDatabase.Builder<AppDatabase> = if (BuildConfig.DEBUG) fallbackToDestructiveMigration() else this

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

    /**
     * Provides the RuleExecutionDao.
     *
     * @param database AppDatabase instance
     * @return RuleExecutionDao
     */
    @Provides
    fun provideRuleExecutionDao(database: AppDatabase): RuleExecutionDao = database.ruleExecutionDao()

    /**
     * Provides the ExtractedFieldValueDao.
     *
     * @param database AppDatabase instance
     * @return ExtractedFieldValueDao
     */
    @Provides
    fun provideExtractedFieldValueDao(database: AppDatabase): ExtractedFieldValueDao = database.extractedFieldValueDao()

    /**
     * Provides the DataBrowserDao.
     *
     * @param database AppDatabase instance
     * @return DataBrowserDao
     */
    @Provides
    fun provideDataBrowserDao(database: AppDatabase): DataBrowserDao = database.dataBrowserDao()
}
