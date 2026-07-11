package dev.gaferneira.notificapp.features.appselection.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.features.appselection.contract.AppSelectionContract.UiEffect
import dev.gaferneira.notificapp.features.appselection.contract.AppSelectionContract.UiEvent
import dev.gaferneira.notificapp.features.appselection.contract.AppSelectionContract.UiState
import dev.gaferneira.notificapp.features.appselection.viewmodel.AppSelectionViewModel

/**
 * App Selection screen for choosing which apps to monitor.
 *
 * Shows installed apps with search functionality and allows user to select
 * which apps to monitor for notification extraction.
 *
 * @param modifier Modifier for the screen
 * @param viewModel ViewModel for state management
 */
@Composable
fun AppSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: AppSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle effects
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    // Refresh and reorder when returning to this screen (e.g., from settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(UiEvent.OnRefresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AppSelectionScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
private fun AppSelectionScreenContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isInitialSetup == false) {
                AppSelectionBackBar(onBackClick = { onEvent(UiEvent.OnBackClicked) })
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
        ) {
            if (uiState.isInitialSetup != false) {
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (uiState.isInitialSetup == true) {
                InitialSetupHeader()
            }

            // Selected apps count - reserves a fixed-height slot so toggling
            // the first app doesn't push the search field/list up and down.
            SelectionCountBanner(hasSelection = uiState.hasSelection, selectedCount = uiState.selectedCount)
            Spacer(modifier = Modifier.height(12.dp))

            // Search field
            SearchField(
                query = uiState.searchQuery,
                onQueryChange = { onEvent(UiEvent.OnSearchQueryChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App list or loading/error states
            AppSelectionListContent(
                uiState = uiState,
                onEvent = onEvent,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Continue/Save button
            ContinueButton(
                enabled = uiState.hasSelection,
                selectedCount = uiState.selectedCount,
                isInitialSetup = uiState.isInitialSetup == true,
                onClick = { onEvent(UiEvent.OnContinueClicked) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (!uiState.hasSelection) {
                Spacer(modifier = Modifier.height(8.dp))
                ContinueDisabledHint()
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Security footer
            SecurityFooter()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Onboarding has 3 real steps: Value Statement, Permission Explanation, App Selection. */
private const val ONBOARDING_TOTAL_STEPS = 3
private const val ONBOARDING_STEP_APP_SELECTION = 2

private val SELECTION_BANNER_HEIGHT = 40.dp

/** Back bar shown when this screen is reached from Settings rather than onboarding. */
@Composable
private fun AppSelectionBackBar(onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.systemBarsPadding(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "Select Apps",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Headline, subtitle and progress dots shown only during initial onboarding setup. */
@Composable
private fun InitialSetupHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        PageIndicator(currentStep = ONBOARDING_STEP_APP_SELECTION, totalSteps = ONBOARDING_TOTAL_STEPS)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Select Data Sources",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose the apps you want to monitor. You can change this later.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Selected-apps count banner. Always reserves [SELECTION_BANNER_HEIGHT] so toggling
 * the first app doesn't push the search field/list up and down.
 */
@Composable
private fun SelectionCountBanner(hasSelection: Boolean, selectedCount: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(SELECTION_BANNER_HEIGHT)) {
        androidx.compose.animation.AnimatedVisibility(
            visible = hasSelection,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "$selectedCount app${if (selectedCount == 1) "" else "s"} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Helper text shown under a disabled Continue/Save button. */
@Composable
private fun ContinueDisabledHint(modifier: Modifier = Modifier) {
    Text(
        text = "Select at least one app to continue",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/** Switches between loading, error, empty and populated app-list states. */
@Composable
private fun AppSelectionListContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            uiState.error != null -> {
                ErrorState(
                    message = uiState.error,
                    onRetry = { onEvent(UiEvent.OnRefresh) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            uiState.filteredApps.isEmpty() -> {
                EmptyState(
                    searchQuery = uiState.searchQuery,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                AppList(
                    apps = uiState.filteredApps,
                    selectedPackages = uiState.selectedPackageNames,
                    onAppToggled = { packageName, isSelected ->
                        onEvent(UiEvent.OnAppToggled(packageName, isSelected))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Page indicator dots at the top, reflecting the real onboarding progress
 * instead of a hardcoded step count.
 *
 * @param currentStep Zero-based index of the active step.
 * @param totalSteps Total number of steps in the flow.
 */
@Composable
private fun PageIndicator(currentStep: Int, totalSteps: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            }
            val width = if (isActive) 24.dp else 8.dp

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = width, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/**
 * Search field for filtering apps.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search apps...") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { keyboardController?.hide() },
        ),
    )
}

/**
 * List of apps with selection checkboxes.
 * Selected apps are shown at the top with animation when items move.
 */
@Composable
private fun AppList(
    apps: List<AppInfo>,
    selectedPackages: Set<String>,
    onAppToggled: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = apps,
            key = { _, app -> app.packageName },
        ) { _, app ->
            val isSelected = app.packageName in selectedPackages
            AppListItem(
                app = app,
                isSelected = isSelected,
                onToggled = { newIsSelected ->
                    onAppToggled(app.packageName, newIsSelected)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Individual app list item.
 */
@Composable
private fun AppListItem(
    app: AppInfo,
    isSelected: Boolean,
    onToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Load the app icon
    val appIcon: ImageBitmap? = remember(app.packageName) {
        try {
            context.packageManager.getApplicationIcon(app.packageName)
                .toBitmap()
                .asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    // Fallback icon based on category
    val fallbackIcon = remember(app.category) {
        when (app.category) {
            "Email" -> Icons.Default.CheckCircle
            "Messaging" -> Icons.Default.CheckCircle
            "Financial" -> Icons.Default.Lock
            "Shopping" -> Icons.Default.ShoppingBag
            else -> Icons.Default.CheckCircle
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            },
        ),
        onClick = { onToggled(!isSelected) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // App icon
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (appIcon != null) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                modifier = Modifier.size(44.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                    } else {
                        Icon(
                            imageVector = fallbackIcon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // App info
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                app.category?.let { category ->
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Checkbox
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(24.dp),
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Continue/Save button with selected count.
 */
@Composable
private fun ContinueButton(
    enabled: Boolean,
    selectedCount: Int,
    isInitialSetup: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonText = if (isInitialSetup) {
        "Continue"
    } else {
        "Save ($selectedCount selected)"
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (isInitialSetup) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Security footer at the bottom.
 */
@Composable
private fun SecurityFooter() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "PROCESSED LOCALLY ON YOUR DEVICE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * Empty state when no apps match search.
 */
@Composable
private fun EmptyState(
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (searchQuery.isBlank()) {
                "No apps found"
            } else {
                "No apps match \"$searchQuery\""
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Error state with retry button.
 */
@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

// Previews
@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun AppSelectionScreenInitialSetupPreview() {
    NotificappTheme {
        AppSelectionScreenContent(
            uiState = UiState(
                availableApps = listOf(
                    AppInfo("com.google.android.gm", "Gmail", "Email"),
                    AppInfo("com.whatsapp", "WhatsApp", "Messaging"),
                    AppInfo("com.revolut.revolut", "Revolut", "Financial"),
                    AppInfo("com.amazon.mShop.android.shopping", "Amazon", "Shopping"),
                    AppInfo("com.microsoft.office.outlook", "Outlook", "Email"),
                    AppInfo("com.uber", "Uber", null),
                ),
                selectedPackageNames = setOf("com.google.android.gm"),
                isLoading = false,
                isInitialSetup = true,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppSelectionScreenInitialSetupPreviewDark() {
    NotificappTheme {
        AppSelectionScreenContent(
            uiState = UiState(
                availableApps = listOf(
                    AppInfo("com.google.android.gm", "Gmail", "Email"),
                    AppInfo("com.whatsapp", "WhatsApp", "Messaging"),
                    AppInfo("com.revolut.revolut", "Revolut", "Financial"),
                    AppInfo("com.amazon.mShop.android.shopping", "Amazon", "Shopping"),
                    AppInfo("com.microsoft.office.outlook", "Outlook", "Email"),
                    AppInfo("com.uber", "Uber", null),
                ),
                selectedPackageNames = setOf("com.google.android.gm"),
                isLoading = false,
                isInitialSetup = true,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun AppSelectionScreenFromSettingsPreview() {
    NotificappTheme {
        AppSelectionScreenContent(
            uiState = UiState(
                availableApps = listOf(
                    AppInfo("com.google.android.gm", "Gmail", "Email"),
                    AppInfo("com.whatsapp", "WhatsApp", "Messaging"),
                    AppInfo("com.revolut.revolut", "Revolut", "Financial"),
                ),
                selectedPackageNames = setOf("com.google.android.gm", "com.whatsapp"),
                isLoading = false,
                isInitialSetup = false,
            ),
            onEvent = {},
        )
    }
}
