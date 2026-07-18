package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import dev.gaferneira.notificapp.core.notification.action.WebhookPayloadBuilder
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_APP_NAME
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_CONTENT
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_TIMESTAMP
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_TITLE
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookPayloadMode
import dev.gaferneira.notificapp.domain.model.getWebhookId
import dev.gaferneira.notificapp.domain.model.getWebhookPayloadMode
import dev.gaferneira.notificapp.domain.model.getWebhookSelectedFields
import dev.gaferneira.notificapp.domain.model.getWebhookTemplate
import dev.gaferneira.notificapp.features.ruleeditor.contract.WebhookConfigContract.UiEvent
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.fakes.FakeWebhookRepository
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Mirrors the ViewModel's private `DEFAULT_CHECKED_BUILTINS` (task 5.6). */
private val DEFAULT_CHECKED_BUILTIN_TOKENS = setOf(
    WEBHOOK_BUILTIN_TITLE,
    WEBHOOK_BUILTIN_CONTENT,
    WEBHOOK_BUILTIN_APP_NAME,
    WEBHOOK_BUILTIN_TIMESTAMP,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WebhookConfigViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var webhookRepository: FakeWebhookRepository
    private lateinit var navigationHandler: NavigationHandler
    private lateinit var viewModel: WebhookConfigViewModel

    private fun amountField(id: String = "field-amount") = createTestField(id = id, name = "Amount", method = RuleField.ExtractionMethod.RegexPattern(pattern = "\\d+"))

    private fun buildViewModel() = WebhookConfigViewModel(
        webhookRepository,
        WebhookPayloadBuilder(),
        navigationHandler,
        testDispatcher,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        webhookRepository = FakeWebhookRepository()
        navigationHandler = NavigationHandler()
        viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `mode toggle updates the config mode`() {
        viewModel.onEvent(UiEvent.Initialize(initial = null, ruleFields = emptyList<RuleField>().toImmutableList()))

        viewModel.onEvent(UiEvent.OnModeChanged(WebhookPayloadMode.TEMPLATE))

        viewModel.uiState.value.config.mode shouldBe WebhookPayloadMode.TEMPLATE
    }

    @Test
    fun `field selection toggles a field id in and out of the selected set`() {
        val field = amountField()
        viewModel.onEvent(UiEvent.Initialize(initial = null, ruleFields = listOf(field).toImmutableList()))

        viewModel.onEvent(UiEvent.OnFieldToggled(field.id, checked = false))
        viewModel.uiState.value.config.selectedFieldIds shouldBe emptySet()

        viewModel.onEvent(UiEvent.OnFieldToggled(field.id, checked = true))
        viewModel.uiState.value.config.selectedFieldIds shouldBe setOf(field.id)
    }

    @Test
    fun `template edit persists to the config and round-trips through the RuleAction mapping`() {
        viewModel.onEvent(UiEvent.Initialize(initial = null, ruleFields = emptyList<RuleField>().toImmutableList()))
        viewModel.onEvent(UiEvent.OnWebhookSelected("wh-1"))
        viewModel.onEvent(UiEvent.OnModeChanged(WebhookPayloadMode.TEMPLATE))

        viewModel.onEvent(UiEvent.OnTemplateChanged("""{"t":"{{title}}"}"""))

        viewModel.uiState.value.config.template shouldBe """{"t":"{{title}}"}"""
        val action = viewModel.uiState.value.toRuleAction()
        action.getWebhookId() shouldBe "wh-1"
        action.getWebhookPayloadMode() shouldBe WebhookPayloadMode.TEMPLATE
        action.getWebhookTemplate() shouldBe """{"t":"{{title}}"}"""
    }

    @Test
    fun `FIELDS mode selection persists to config`() {
        val field = amountField()
        viewModel.onEvent(UiEvent.Initialize(initial = null, ruleFields = listOf(field).toImmutableList()))
        viewModel.onEvent(UiEvent.OnWebhookSelected("wh-1"))

        val action = viewModel.uiState.value.toRuleAction()
        action.getWebhookSelectedFields() shouldBe setOf("field.${field.id}") + DEFAULT_CHECKED_BUILTIN_TOKENS
    }

    @Test
    fun `preview surfaces an unknown-token warning in TEMPLATE mode`() {
        viewModel.onEvent(UiEvent.Initialize(initial = null, ruleFields = emptyList<RuleField>().toImmutableList()))
        viewModel.onEvent(UiEvent.OnWebhookSelected("wh-1"))
        viewModel.onEvent(UiEvent.OnModeChanged(WebhookPayloadMode.TEMPLATE))
        viewModel.onEvent(UiEvent.OnTemplateChanged("""{"x":"{{totally_unknown}}"}"""))

        viewModel.onEvent(UiEvent.OnPreviewClicked)

        viewModel.uiState.value.previewWarning shouldBe "Unknown token(s): {{totally_unknown}}"
    }

    @Test
    fun `preview has no warning for a fully known TEMPLATE`() {
        viewModel.onEvent(UiEvent.Initialize(initial = null, ruleFields = emptyList<RuleField>().toImmutableList()))
        viewModel.onEvent(UiEvent.OnWebhookSelected("wh-1"))
        viewModel.onEvent(UiEvent.OnModeChanged(WebhookPayloadMode.TEMPLATE))
        viewModel.onEvent(UiEvent.OnTemplateChanged("""{"t":"{{title}}"}"""))

        viewModel.onEvent(UiEvent.OnPreviewClicked)

        viewModel.uiState.value.previewWarning shouldBe null
        viewModel.uiState.value.previewJson shouldBe """{"t":"Sample notification title"}"""
    }

    @Test
    fun `picker reflects webhooks observed from the repository`() = runTest(testDispatcher) {
        val webhook = Webhook(id = "wh-1", name = "Home Assistant", url = "https://ha.local/api/hook")
        webhookRepository.setWebhooks(listOf(webhook))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.value.webhooks.map { it.id } shouldBe listOf("wh-1")
    }
}
