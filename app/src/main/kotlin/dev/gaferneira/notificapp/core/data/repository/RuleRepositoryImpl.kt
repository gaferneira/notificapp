package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.common.toFailureResult
import dev.gaferneira.notificapp.core.data.local.dao.RuleDao
import dev.gaferneira.notificapp.core.data.local.dao.SelectedAppDao
import dev.gaferneira.notificapp.core.data.local.entity.RuleActionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleFieldEntity
import dev.gaferneira.notificapp.core.data.local.mapper.RuleActionMapper
import dev.gaferneira.notificapp.core.data.local.mapper.RuleMapper
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Database-backed implementation of RuleRepository.
 *
 * Uses Room for persistence with JSON serialization for complex nested objects.
 */
@Singleton
internal class RuleRepositoryImpl @Inject constructor(
    private val ruleDao: RuleDao,
    private val selectedAppDao: SelectedAppDao,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleRepository {

    override fun observeAllRules(): Flow<List<Rule>> = ruleDao.observeAll().map { entities ->
        entities.map { entity ->
            // Load target apps, actions (with their SAVE_DATA fields), and conditions for each rule
            val apps = if (entity.isGlobal) null else loadTargetApps(entity.id)
            val actions = ruleDao.getActionsForRule(entity.id)
            val fields = loadSaveDataFields(actions)
            val conditions = ruleDao.getConditionsForRule(entity.id)
            RuleMapper.toDomain(entity, fields, conditions, actions, apps)
        }
    }

    override suspend fun getAllRules(): Result<List<Rule>> = withContext(ioDispatcher) {
        try {
            val entities = ruleDao.getAll()
            val rules = entities.map { entity ->
                val apps = loadTargetApps(entity.id)
                val actions = ruleDao.getActionsForRule(entity.id)
                val fields = loadSaveDataFields(actions)
                val conditions = ruleDao.getConditionsForRule(entity.id)
                RuleMapper.toDomain(entity, fields, conditions, actions, apps)
            }
            Result.success(rules)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get all rules")
            e.toFailureResult()
        }
    }

    override suspend fun getRule(id: String): Result<Rule?> = withContext(ioDispatcher) {
        try {
            val entity = ruleDao.getById(id)
            val rule = entity?.let {
                val apps = loadTargetApps(it.id)
                val actions = ruleDao.getActionsForRule(it.id)
                val fields = loadSaveDataFields(actions)
                val conditions = ruleDao.getConditionsForRule(it.id)
                RuleMapper.toDomain(it, fields, conditions, actions, apps)
            }
            Result.success(rule)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get rule: $id")
            e.toFailureResult()
        }
    }

    override suspend fun getRulesForApp(packageName: String): Result<List<Rule>> = withContext(ioDispatcher) {
        try {
            val entities = ruleDao.getRulesForApp(packageName)
            val rules = entities.map { entity ->
                val apps = loadTargetApps(entity.id)
                val actions = ruleDao.getActionsForRule(entity.id)
                val fields = loadSaveDataFields(actions)
                val conditions = ruleDao.getConditionsForRule(entity.id)
                RuleMapper.toDomain(entity, fields, conditions, actions, apps)
            }
            Result.success(rules)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get rules for app: $packageName")
            e.toFailureResult()
        }
    }

    override suspend fun saveRule(rule: Rule): Result<Unit> = withContext(ioDispatcher) {
        try {
            val entity = RuleMapper.toEntity(rule)
            val actionEntities = RuleMapper.actionsToEntityList(rule.actions, rule.id)
            val fieldEntities = RuleMapper.fieldsToEntityList(rule.actions)
            val conditionEntities = RuleMapper.conditionsToEntityList(rule.conditions, rule.id)
            val apps = rule.targetApps?.map { it.packageName }
            ruleDao.saveRuleWithRelatedData(entity, fieldEntities, conditionEntities, actionEntities, apps)
            Timber.d("Saved rule: ${rule.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save rule: ${rule.id}")
            e.toFailureResult()
        }
    }

    override suspend fun updateRule(rule: Rule): Result<Unit> = withContext(ioDispatcher) {
        try {
            val entity = RuleMapper.toEntity(rule)
            val actionEntities = RuleMapper.actionsToEntityList(rule.actions, rule.id)
            val fieldEntities = RuleMapper.fieldsToEntityList(rule.actions)
            val conditionEntities = RuleMapper.conditionsToEntityList(rule.conditions, rule.id)
            val apps = rule.targetApps?.map { it.packageName }
            ruleDao.saveRuleWithRelatedData(entity, fieldEntities, conditionEntities, actionEntities, apps)
            Timber.d("Updated rule: ${rule.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update rule: ${rule.id}")
            e.toFailureResult()
        }
    }

    /**
     * Load the fields owned by a rule's `SAVE_DATA` action, if it has one. Loading by the
     * already-fetched action's id avoids a redundant rule-keyed field query - `rule_fields` is
     * keyed on `action_id`, not `rule_id`.
     */
    private suspend fun loadSaveDataFields(actions: List<RuleActionEntity>): List<RuleFieldEntity> {
        val saveDataActionId = actions.firstOrNull { it.type == ActionType.SAVE_DATA.name }?.id ?: return emptyList()
        return ruleDao.getFieldsForAction(saveDataActionId)
    }

    override suspend fun deleteRule(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            ruleDao.deleteRuleWithRelatedData(id)
            Timber.d("Deleted rule: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete rule: $id")
            e.toFailureResult()
        }
    }

    override suspend fun toggleRuleActive(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            ruleDao.toggleActive(id)
            Timber.d("Toggled rule: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle rule: $id")
            e.toFailureResult()
        }
    }

    override suspend fun getRuleCount(): Result<Int> = withContext(ioDispatcher) {
        try {
            Result.success(ruleDao.getCount())
        } catch (e: Exception) {
            Timber.e(e, "Failed to get rule count")
            e.toFailureResult()
        }
    }

    override suspend fun getActiveRuleCount(): Result<Int> = withContext(ioDispatcher) {
        try {
            Result.success(ruleDao.getActiveCount())
        } catch (e: Exception) {
            Timber.e(e, "Failed to get active rule count")
            e.toFailureResult()
        }
    }

    override suspend fun isImageUriReferencedByOtherAlarmAction(
        uri: String,
        excludingActionId: String,
    ): Boolean = withContext(ioDispatcher) {
        val alarmActionEntities = ruleDao.getActionsByType(ActionType.CREATE_ALARM.name)
        alarmActionEntities.any { entity ->
            entity.id != excludingActionId && RuleActionMapper.toDomain(entity).getAlarmBackgroundImageUri() == uri
        }
    }

    /**
     * Load target apps for a rule.
     */
    private suspend fun loadTargetApps(ruleId: String): List<AppInfo>? {
        val packageNames = ruleDao.getTargetAppsForRule(ruleId).takeIf { it.isNotEmpty() } ?: return null
        val entities = selectedAppDao.getByPackageNames(packageNames)
        return entities.map { entity ->
            AppInfo(
                packageName = entity.packageName,
                name = entity.appName,
            )
        }.takeIf { it.isNotEmpty() }
    }
}
