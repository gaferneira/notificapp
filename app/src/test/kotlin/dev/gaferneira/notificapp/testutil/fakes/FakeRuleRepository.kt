package dev.gaferneira.notificapp.testutil.fakes

import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Deterministic in-memory [RuleRepository] fake for VM tests, backed by a [MutableStateFlow] so
 * writes are immediately visible to [observeAllRules] collectors - unlike a dynamic mock returning
 * a snapshot flow, this lets tests assert "the observed stream reflects a write" behavior.
 *
 * [saveError] is an opt-in failure injection hook: set it before an operation to make the next
 * mutating call return `Result.failure(saveError)` instead of succeeding.
 */
class FakeRuleRepository(initial: List<Rule> = emptyList()) : RuleRepository {

    private val rules = MutableStateFlow(initial)

    var saveError: Throwable? = null

    fun currentRules(): List<Rule> = rules.value

    override fun observeAllRules(): Flow<List<Rule>> = rules.asStateFlow()

    override suspend fun getAllRules(): Result<List<Rule>> = Result.success(rules.value)

    override suspend fun getRule(id: String): Result<Rule?> = Result.success(rules.value.find { it.id == id })

    override suspend fun getRulesForApp(packageName: String): Result<List<Rule>> = Result.success(
        rules.value.filter { rule -> rule.targetApps == null || rule.targetApps.any { it.packageName == packageName } },
    )

    override suspend fun saveRule(rule: Rule): Result<Unit> {
        saveError?.let { return Result.failure(it) }
        rules.update { list -> list.filterNot { it.id == rule.id } + rule }
        return Result.success(Unit)
    }

    override suspend fun updateRule(rule: Rule): Result<Unit> {
        saveError?.let { return Result.failure(it) }
        rules.update { list -> list.map { if (it.id == rule.id) rule else it } }
        return Result.success(Unit)
    }

    override suspend fun deleteRule(id: String): Result<Unit> {
        rules.update { list -> list.filterNot { it.id == id } }
        return Result.success(Unit)
    }

    override suspend fun toggleRuleActive(id: String): Result<Unit> {
        rules.update { list -> list.map { if (it.id == id) it.copy(isActive = !it.isActive) else it } }
        return Result.success(Unit)
    }

    override suspend fun getRuleCount(): Result<Int> = Result.success(rules.value.size)

    override suspend fun getActiveRuleCount(): Result<Int> = Result.success(rules.value.count { it.isActive })

    override suspend fun isImageUriReferencedByOtherAlarmAction(uri: String, excludingActionId: String): Result<Boolean> = Result.success(false)
}
