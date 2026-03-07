package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.data.local.dao.RuleDao
import dev.gaferneira.notificapp.core.data.local.mapper.RuleMapper
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
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
class RuleRepositoryImpl @Inject constructor(
    private val ruleDao: RuleDao,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleRepository {

    override fun observeAllRules(): Flow<List<Rule>> = ruleDao.observeAll().map { entities ->
        entities.map { entity ->
            // Load target apps for each rule
            val apps = if (entity.isGlobal) null else loadTargetApps(entity.id)
            RuleMapper.toDomain(entity, apps)
        }
    }

    override suspend fun getAllRules(): Result<List<Rule>> = withContext(ioDispatcher) {
        try {
            val entities = ruleDao.getAll()
            val rules = entities.map { entity ->
                val apps = loadTargetApps(entity.id)
                RuleMapper.toDomain(entity, apps)
            }
            Result.success(rules)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get all rules")
            Result.failure(e)
        }
    }

    override suspend fun getRule(id: String): Result<Rule?> = withContext(ioDispatcher) {
        try {
            val entity = ruleDao.getById(id)
            val rule = entity?.let {
                val apps = loadTargetApps(it.id)
                RuleMapper.toDomain(it, apps)
            }
            Result.success(rule)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get rule: $id")
            Result.failure(e)
        }
    }

    override suspend fun getRulesForApp(packageName: String): Result<List<Rule>> = withContext(ioDispatcher) {
        try {
            val entities = ruleDao.getRulesForApp(packageName)
            val rules = entities.map { entity ->
                val apps = loadTargetApps(entity.id)
                RuleMapper.toDomain(entity, apps)
            }
            Result.success(rules)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get rules for app: $packageName")
            Result.failure(e)
        }
    }

    override suspend fun saveRule(rule: Rule): Result<Unit> = withContext(ioDispatcher) {
        try {
            val entity = RuleMapper.toEntity(rule)
            val apps = rule.targetApps?.filter { it.isNotBlank() }
            ruleDao.saveRuleWithApps(entity, apps)
            Timber.d("Saved rule: ${rule.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save rule: ${rule.id}")
            Result.failure(e)
        }
    }

    override suspend fun updateRule(rule: Rule): Result<Unit> = withContext(ioDispatcher) {
        try {
            val entity = RuleMapper.toEntity(rule)
            val apps = rule.targetApps?.filter { it.isNotBlank() }
            ruleDao.saveRuleWithApps(entity, apps)
            Timber.d("Updated rule: ${rule.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update rule: ${rule.id}")
            Result.failure(e)
        }
    }

    override suspend fun deleteRule(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            ruleDao.delete(id)
            Timber.d("Deleted rule: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete rule: $id")
            Result.failure(e)
        }
    }

    override suspend fun toggleRuleActive(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            ruleDao.toggleActive(id)
            Timber.d("Toggled rule: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle rule: $id")
            Result.failure(e)
        }
    }

    override suspend fun getRuleCount(): Result<Int> = withContext(ioDispatcher) {
        try {
            Result.success(ruleDao.getCount())
        } catch (e: Exception) {
            Timber.e(e, "Failed to get rule count")
            Result.failure(e)
        }
    }

    override suspend fun getActiveRuleCount(): Result<Int> = withContext(ioDispatcher) {
        try {
            Result.success(ruleDao.getActiveCount())
        } catch (e: Exception) {
            Timber.e(e, "Failed to get active rule count")
            Result.failure(e)
        }
    }

    /**
     * Load target apps for a rule.
     */
    private suspend fun loadTargetApps(ruleId: String): List<String>? = ruleDao.getTargetAppsForRule(ruleId).takeIf { it.isNotEmpty() }
}
