package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.data.local.dao.RuleDao
import dev.gaferneira.notificapp.core.data.local.dao.SelectedAppDao
import dev.gaferneira.notificapp.core.data.local.entity.RuleActionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleFieldEntity
import dev.gaferneira.notificapp.core.data.local.mapper.RuleActionMapper
import dev.gaferneira.notificapp.core.data.local.mapper.RuleMapper
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.getAlarmBackgroundImageUri
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
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

    override fun observeAllRules(): Flow<List<Rule>> = ruleDao.observeAll()
        .map { entities -> assembleRules(entities) }
        .flowOn(ioDispatcher)

    override suspend fun getAllRules(): Result<List<Rule>> = withContext(ioDispatcher) {
        dbCatching("Failed to get all rules") { assembleRules(ruleDao.getAll()) }
    }

    override suspend fun getRule(id: String): Result<Rule?> = withContext(ioDispatcher) {
        dbCatching("Failed to get rule: $id") {
            ruleDao.getById(id)?.let {
                val apps = loadTargetApps(it.id)
                val actions = ruleDao.getActionsForRule(it.id)
                val fields = loadSaveDataFields(actions)
                val conditions = ruleDao.getConditionsForRule(it.id)
                RuleMapper.toDomain(it, fields, conditions, actions, apps)
            }
        }
    }

    override suspend fun getRulesForApp(packageName: String): Result<List<Rule>> = withContext(ioDispatcher) {
        dbCatching("Failed to get rules for app: $packageName") { assembleRules(ruleDao.getRulesForApp(packageName)) }
    }

    override suspend fun saveRule(rule: Rule): Result<Unit> = withContext(ioDispatcher) {
        dbCatching("Failed to save rule: ${rule.id}") {
            val entity = RuleMapper.toEntity(rule)
            val actionEntities = RuleMapper.actionsToEntityList(rule.actions, rule.id)
            val fieldEntities = RuleMapper.fieldsToEntityList(rule.actions)
            val conditionEntities = RuleMapper.conditionsToEntityList(rule.conditions, rule.id)
            val apps = rule.targetApps?.map { it.packageName }
            ruleDao.saveRuleWithRelatedData(entity, fieldEntities, conditionEntities, actionEntities, apps)
            Timber.d("Saved rule: ${rule.id}")
        }
    }

    override suspend fun updateRule(rule: Rule): Result<Unit> = withContext(ioDispatcher) {
        dbCatching("Failed to update rule: ${rule.id}") {
            val entity = RuleMapper.toEntity(rule)
            val actionEntities = RuleMapper.actionsToEntityList(rule.actions, rule.id)
            val fieldEntities = RuleMapper.fieldsToEntityList(rule.actions)
            val conditionEntities = RuleMapper.conditionsToEntityList(rule.conditions, rule.id)
            val apps = rule.targetApps?.map { it.packageName }
            ruleDao.saveRuleWithRelatedData(entity, fieldEntities, conditionEntities, actionEntities, apps)
            Timber.d("Updated rule: ${rule.id}")
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

    /**
     * Assemble full [Rule] domain models for a batch of rule entities using a fixed handful of
     * `IN (:ruleIds)` queries instead of ~4 per-rule round-trips (conditions, actions, fields,
     * target apps) - the N+1 shape this replaces multiplied with the rule count on every
     * [observeAllRules] emission and on every [getAllRules]/[getRulesForApp] call.
     */
    private suspend fun assembleRules(entities: List<RuleEntity>): List<Rule> {
        if (entities.isEmpty()) return emptyList()
        val ruleIds = entities.map { it.id }

        val conditionsByRule = ruleDao.getConditionsForRules(ruleIds).groupBy { it.ruleId }
        val actionsByRule = ruleDao.getActionsForRules(ruleIds).groupBy { it.ruleId }
        val saveDataActionIds = actionsByRule.values.flatten()
            .filter { it.type == ActionType.SAVE_DATA.name }
            .map { it.id }
        val fieldsByAction = ruleDao.getFieldsForActions(saveDataActionIds).groupBy { it.actionId }

        val targetPackagesByRule = ruleDao.getTargetAppsForRules(ruleIds).groupBy({ it.ruleId }, { it.packageName })
        val allTargetPackages = targetPackagesByRule.values.flatten().distinct()
        val appInfoByPackage = if (allTargetPackages.isEmpty()) {
            emptyMap()
        } else {
            selectedAppDao.getByPackageNames(allTargetPackages).associate { it.packageName to AppInfo(it.packageName, it.appName) }
        }

        return entities.map { entity ->
            val actions = actionsByRule[entity.id].orEmpty()
            val fields = actions.filter { it.type == ActionType.SAVE_DATA.name }
                .flatMap { fieldsByAction[it.id].orEmpty() }
            val apps = targetPackagesByRule[entity.id]
                ?.mapNotNull { appInfoByPackage[it] }
                ?.takeIf { it.isNotEmpty() }
            RuleMapper.toDomain(entity, fields, conditionsByRule[entity.id].orEmpty(), actions, apps)
        }
    }

    override suspend fun deleteRule(id: String): Result<Unit> = withContext(ioDispatcher) {
        dbCatching("Failed to delete rule: $id") {
            ruleDao.deleteRuleWithRelatedData(id)
            Timber.d("Deleted rule: $id")
        }
    }

    override suspend fun toggleRuleActive(id: String): Result<Unit> = withContext(ioDispatcher) {
        dbCatching("Failed to toggle rule: $id") {
            ruleDao.toggleActive(id)
            Timber.d("Toggled rule: $id")
        }
    }

    override suspend fun getRuleCount(): Result<Int> = withContext(ioDispatcher) {
        dbCatching("Failed to get rule count") { ruleDao.getCount() }
    }

    override suspend fun getActiveRuleCount(): Result<Int> = withContext(ioDispatcher) {
        dbCatching("Failed to get active rule count") { ruleDao.getActiveCount() }
    }

    override suspend fun isImageUriReferencedByOtherAlarmAction(
        uri: String,
        excludingActionId: String,
    ): Result<Boolean> = withContext(ioDispatcher) {
        dbCatching("Failed to check if image URI is referenced by another alarm action") {
            val candidates = ruleDao.getActionsByTypeReferencingUri(ActionType.CREATE_ALARM.name, uri, excludingActionId)
            candidates.any { entity -> RuleActionMapper.toDomain(entity).getAlarmBackgroundImageUri() == uri }
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
