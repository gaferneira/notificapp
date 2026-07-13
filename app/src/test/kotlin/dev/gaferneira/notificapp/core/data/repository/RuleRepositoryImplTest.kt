package dev.gaferneira.notificapp.core.data.repository

import app.cash.turbine.test
import dev.gaferneira.notificapp.core.data.local.dao.RuleDao
import dev.gaferneira.notificapp.core.data.local.dao.SelectedAppDao
import dev.gaferneira.notificapp.core.data.local.entity.RuleActionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleConditionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleTargetAppEntity
import dev.gaferneira.notificapp.core.data.local.entity.SelectedAppEntity
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.Rule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.sql.SQLException

@OptIn(ExperimentalCoroutinesApi::class)
class RuleRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val ruleDao: RuleDao = mockk()
    private val selectedAppDao: SelectedAppDao = mockk()
    private val repository = RuleRepositoryImpl(ruleDao, selectedAppDao, testDispatcher)

    private fun ruleEntity(id: String, isGlobal: Boolean = false) = RuleEntity(
        id = id,
        name = "Rule $id",
        description = null,
        category = null,
        isGlobal = isGlobal,
    )

    private fun actionEntity(id: String, ruleId: String, type: String) = RuleActionEntity(
        id = id,
        ruleId = ruleId,
        type = type,
        config = "{}",
    )

    private fun conditionEntity(id: String, ruleId: String) = RuleConditionEntity(
        id = id,
        ruleId = ruleId,
        // Storage payload keys the MatchingCondition/MatchingOperator enum constant names
        // (RuleConditionMapper.toDto uses `.name`), not the wire-format strings.
        payload = """{"type":"content_match","id":"$id","condition":"TEXT_CONTENT","operator":"CONTAINS","value":"purchase"}""",
    )

    @Test
    fun `getAllRules reassembles the full aggregate from every dao source`() = runTest(testDispatcher) {
        val entity = ruleEntity(id = "r1", isGlobal = false)
        coEvery { ruleDao.getAll() } returns listOf(entity)
        coEvery { ruleDao.getConditionsForRules(listOf("r1")) } returns listOf(conditionEntity("c1", "r1"))
        coEvery { ruleDao.getActionsForRules(listOf("r1")) } returns listOf(actionEntity("a1", "r1", "SAVE_DATA"))
        coEvery { ruleDao.getFieldsForActions(listOf("a1")) } returns emptyList()
        coEvery { ruleDao.getTargetAppsForRules(listOf("r1")) } returns listOf(RuleTargetAppEntity("r1", "com.bank"))
        coEvery { selectedAppDao.getByPackageNames(listOf("com.bank")) } returns
            listOf(SelectedAppEntity(packageName = "com.bank", appName = "Bank"))

        val result = repository.getAllRules()

        result.isSuccess shouldBe true
        val rule = result.getOrThrow().single()
        rule.id shouldBe "r1"
        rule.targetApps!!.map { it.packageName } shouldBe listOf("com.bank")
        rule.actions.single().type shouldBe ActionType.SAVE_DATA
        rule.conditions.single().id shouldBe "c1"
    }

    @Test
    fun `getAllRules maps a dao exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { ruleDao.getAll() } throws SQLException("db locked")

        val result = repository.getAllRules()

        result.isFailure shouldBe true
    }

    @Test
    fun `global rule yields null targetApps even when target-app rows exist`() = runTest(testDispatcher) {
        coEvery { ruleDao.getAll() } returns listOf(ruleEntity(id = "g", isGlobal = true))
        coEvery { ruleDao.getConditionsForRules(listOf("g")) } returns emptyList()
        coEvery { ruleDao.getActionsForRules(listOf("g")) } returns emptyList()
        coEvery { ruleDao.getFieldsForActions(emptyList()) } returns emptyList()
        coEvery { ruleDao.getTargetAppsForRules(listOf("g")) } returns listOf(RuleTargetAppEntity("g", "com.bank"))
        coEvery { selectedAppDao.getByPackageNames(listOf("com.bank")) } returns
            listOf(SelectedAppEntity(packageName = "com.bank", appName = "Bank"))

        val result = repository.getAllRules()

        result.getOrThrow().single().targetApps shouldBe null
    }

    @Test
    fun `getAllRules with no rules skips every batch dao call`() = runTest(testDispatcher) {
        coEvery { ruleDao.getAll() } returns emptyList()

        val result = repository.getAllRules()

        result.getOrThrow() shouldBe emptyList()
        coVerify(exactly = 0) { ruleDao.getConditionsForRules(any()) }
        coVerify(exactly = 0) { selectedAppDao.getByPackageNames(any()) }
    }

    @Test
    fun `observeAllRules assembles rules from the reactive dao stream`() = runTest(testDispatcher) {
        val entity = ruleEntity(id = "r1", isGlobal = true)
        coEvery { ruleDao.observeAll() } returns MutableStateFlow(listOf(entity))
        coEvery { ruleDao.getConditionsForRules(listOf("r1")) } returns emptyList()
        coEvery { ruleDao.getActionsForRules(listOf("r1")) } returns emptyList()
        coEvery { ruleDao.getFieldsForActions(emptyList()) } returns emptyList()
        coEvery { ruleDao.getTargetAppsForRules(listOf("r1")) } returns emptyList()

        repository.observeAllRules().test {
            awaitItem().single().id shouldBe "r1"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRule returns null when the dao has no matching row`() = runTest(testDispatcher) {
        coEvery { ruleDao.getById("missing") } returns null

        val result = repository.getRule("missing")

        result.getOrThrow() shouldBe null
    }

    @Test
    fun `getRule assembles a single rule from its own dao calls`() = runTest(testDispatcher) {
        val entity = ruleEntity(id = "r1", isGlobal = true)
        coEvery { ruleDao.getById("r1") } returns entity
        coEvery { ruleDao.getTargetAppsForRule("r1") } returns emptyList()
        coEvery { ruleDao.getActionsForRule("r1") } returns emptyList()
        coEvery { ruleDao.getConditionsForRule("r1") } returns emptyList()

        val result = repository.getRule("r1")

        result.getOrThrow()?.id shouldBe "r1"
    }

    @Test
    fun `saveRule persists the mapped entity and related data`() = runTest(testDispatcher) {
        coEvery { ruleDao.saveRuleWithRelatedData(any(), any(), any(), any(), any()) } returns Unit
        val rule = Rule(id = "r1", name = "Bank payment", description = null)

        val result = repository.saveRule(rule)

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { ruleDao.saveRuleWithRelatedData(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `deleteRule delegates to the combined dao delete`() = runTest(testDispatcher) {
        coEvery { ruleDao.deleteRuleWithRelatedData("r1") } returns Unit

        val result = repository.deleteRule("r1")

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { ruleDao.deleteRuleWithRelatedData("r1") }
    }

    @Test
    fun `toggleRuleActive delegates to the dao`() = runTest(testDispatcher) {
        coEvery { ruleDao.toggleActive("r1") } returns Unit

        val result = repository.toggleRuleActive("r1")

        result.isSuccess shouldBe true
    }

    @Test
    fun `getRuleCount and getActiveRuleCount return the dao counts`() = runTest(testDispatcher) {
        coEvery { ruleDao.getCount() } returns 5
        coEvery { ruleDao.getActiveCount() } returns 3

        repository.getRuleCount().getOrThrow() shouldBe 5
        repository.getActiveRuleCount().getOrThrow() shouldBe 3
    }

    @Test
    fun `isImageUriReferencedByOtherAlarmAction is true when a candidate action's config resolves the uri`() = runTest(testDispatcher) {
        val candidate = actionEntity("a1", "r1", "CREATE_ALARM").copy(
            config = """{"alarm_background_image_uri":"content://media/bg.jpg"}""",
        )
        coEvery {
            ruleDao.getActionsByTypeReferencingUri("CREATE_ALARM", "content://media/bg.jpg", "excluded")
        } returns listOf(candidate)

        val result = repository.isImageUriReferencedByOtherAlarmAction("content://media/bg.jpg", "excluded")

        result.getOrThrow() shouldBe true
    }

    @Test
    fun `isImageUriReferencedByOtherAlarmAction is false when there are no candidates`() = runTest(testDispatcher) {
        coEvery {
            ruleDao.getActionsByTypeReferencingUri("CREATE_ALARM", "content://media/bg.jpg", "excluded")
        } returns emptyList()

        val result = repository.isImageUriReferencedByOtherAlarmAction("content://media/bg.jpg", "excluded")

        result.getOrThrow() shouldBe false
    }
}
