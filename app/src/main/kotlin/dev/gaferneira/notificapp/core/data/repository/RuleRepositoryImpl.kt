package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.ExtractionRule
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory implementation of RuleRepository for MVP.
 *
 * In production, this would be backed by a database.
 */
@Singleton
class RuleRepositoryImpl @Inject constructor(
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleRepository {

    // In-memory storage for MVP
    private val rules = mutableListOf<ExtractionRule>()
    private val rulesFlow = MutableStateFlow<List<ExtractionRule>>(emptyList())

    override fun observeAllRules(): Flow<List<ExtractionRule>> = rulesFlow.asStateFlow()

    override suspend fun getAllRules(): Result<List<ExtractionRule>> = withContext(ioDispatcher) {
        Result.success(rules.toList())
    }

    override suspend fun getRule(id: String): Result<ExtractionRule?> = withContext(ioDispatcher) {
        val rule = rules.find { it.id == id }
        Result.success(rule)
    }

    override suspend fun getRulesForApp(packageName: String): Result<List<ExtractionRule>> = withContext(ioDispatcher) {
        val appRules = rules.filter { rule ->
            when {
                rule.targetApps == null -> true
                rule.targetApps.isEmpty() -> true
                rule.targetApps.contains(packageName) -> true
                else -> false
            }
        }
        Result.success(appRules)
    }

    override suspend fun saveRule(rule: ExtractionRule): Result<Unit> = withContext(ioDispatcher) {
        try {
            rules.add(rule)
            rulesFlow.value = rules.toList()
            Timber.d("Saved rule: ${rule.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save rule: ${rule.id}")
            Result.failure(e)
        }
    }

    override suspend fun updateRule(rule: ExtractionRule): Result<Unit> = withContext(ioDispatcher) {
        try {
            val index = rules.indexOfFirst { it.id == rule.id }
            if (index >= 0) {
                rules[index] = rule
                rulesFlow.value = rules.toList()
                Timber.d("Updated rule: ${rule.id}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update rule: ${rule.id}")
            Result.failure(e)
        }
    }

    override suspend fun deleteRule(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            rules.removeAll { it.id == id }
            rulesFlow.value = rules.toList()
            Timber.d("Deleted rule: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete rule: $id")
            Result.failure(e)
        }
    }

    override suspend fun toggleRuleActive(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val index = rules.indexOfFirst { it.id == id }
            if (index >= 0) {
                val rule = rules[index]
                rules[index] = rule.copy(isActive = !rule.isActive)
                rulesFlow.value = rules.toList()
                Timber.d("Toggled rule: $id, now active: ${rules[index].isActive}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle rule: $id")
            Result.failure(e)
        }
    }

    override suspend fun getRuleCount(): Result<Int> = withContext(ioDispatcher) {
        Result.success(rules.size)
    }

    override suspend fun getActiveRuleCount(): Result<Int> = withContext(ioDispatcher) {
        Result.success(rules.count { it.isActive })
    }
}
