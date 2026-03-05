package dev.gaferneira.notificapp.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.dao.SelectedAppDao
import dev.gaferneira.notificapp.core.data.local.entity.NotificationEntity
import dev.gaferneira.notificapp.core.data.local.entity.SelectedAppEntity

/**
 * Room database for Notificapp.
 *
 * Database version: 1
 * Entities: SelectedAppEntity, NotificationEntity
 */
@Database(
    entities = [
        SelectedAppEntity::class,
        NotificationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun selectedAppDao(): SelectedAppDao
    abstract fun notificationDao(): NotificationDao
}
