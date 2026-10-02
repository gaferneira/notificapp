package dev.gaferneira.notificapp.features.ruletemplates.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplates
import dev.gaferneira.notificapp.features.ruletemplates.contract.RuleTemplatesEffect
import dev.gaferneira.notificapp.features.ruletemplates.contract.RuleTemplatesEvent
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleTemplatesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: RuleTemplatesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = RuleTemplatesViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state lists every template with distinct categories and no filter`() {
        // Given: a freshly created ViewModel

        // Then: all templates are shown, unfiltered, with one chip per distinct category
        val state = viewModel.uiState.value
        state.selectedCategory shouldBe null
        state.templates.shouldContainExactly(RuleTemplates.all)
        state.categories.shouldContainExactly(RuleTemplates.all.map { it.category }.distinct())
    }

    @Test
    fun `selecting a category filters the templates to that category`() {
        // Given: a category present in the catalog
        val category = RuleTemplates.all.first().category

        // When: selecting it
        viewModel.onEvent(RuleTemplatesEvent.OnCategorySelected(category))

        // Then: only templates of that category remain
        val state = viewModel.uiState.value
        state.selectedCategory shouldBe category
        state.templates.shouldContainExactly(RuleTemplates.all.filter { it.category == category })
    }

    @Test
    fun `selecting All restores the full list`() {
        // Given: an active category filter
        viewModel.onEvent(RuleTemplatesEvent.OnCategorySelected(RuleTemplates.all.first().category))

        // When: selecting "All" (null)
        viewModel.onEvent(RuleTemplatesEvent.OnCategorySelected(null))

        // Then: the unfiltered list is back
        val state = viewModel.uiState.value
        state.selectedCategory shouldBe null
        state.templates.shouldContainExactly(RuleTemplates.all)
    }

    @Test
    fun `clicking a template opens the editor with its asset file name`() = runTest(testDispatcher) {
        val template = RuleTemplates.all.first()

        viewModel.effect.test {
            // When: clicking a template
            viewModel.onEvent(RuleTemplatesEvent.OnTemplateClick(template))
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the editor opens pre-populated from that template
            awaitItem() shouldBe RuleTemplatesEffect.OpenRuleEditor(template.assetFileName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `start from scratch opens the editor without a template`() = runTest(testDispatcher) {
        viewModel.effect.test {
            // When: choosing to start from scratch
            viewModel.onEvent(RuleTemplatesEvent.OnStartFromScratch)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: the editor opens blank
            awaitItem() shouldBe RuleTemplatesEffect.OpenRuleEditor(null)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
