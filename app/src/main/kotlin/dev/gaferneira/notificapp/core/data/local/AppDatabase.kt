package dev.gaferneira.notificapp.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.gaferneira.notificapp.core.data.local.converter.RuleTypeConverters
import dev.gaferneira.notificapp.core.data.local.dao.ExtractedFieldValueDao
import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleExecutionDao
import dev.gaferneira.notificapp.core.data.local.dao.SelectedAppDao
import dev.gaferneira.notificapp.core.data.local.entity.ExtractedFieldValueEntity
import dev.gaferneira.notificapp.core.data.local.entity.NotificationEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleActionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleConditionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleExecutionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleFieldEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleTargetAppEntity
import dev.gaferneira.notificapp.core.data.local.entity.SelectedAppEntity

/**
 * Room database for Notificapp.
 *
 * Database version: 6
 * Entities: SelectedAppEntity, NotificationEntity, RuleEntity, RuleTargetAppEntity, RuleFieldEntity,
 *           RuleConditionEntity, RuleActionEntity, RuleExecutionEntity, ExtractedFieldValueEntity
 */
@Database(
    entities = [
        SelectedAppEntity::class,
        NotificationEntity::class,
        RuleEntity::class,
        RuleTargetAppEntity::class,
        RuleFieldEntity::class,
        RuleConditionEntity::class,
        RuleActionEntity::class,
        RuleExecutionEntity::class,
        ExtractedFieldValueEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RuleTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun selectedAppDao(): SelectedAppDao
    abstract fun notificationDao(): NotificationDao
    abstract fun ruleDao(): RuleDao
    abstract fun ruleExecutionDao(): RuleExecutionDao
    abstract fun extractedFieldValueDao(): ExtractedFieldValueDao
}
