package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/**
 * Dashboard section header: an uppercase "eyebrow" [title] (announced as a heading) with an optional
 * trailing text action such as "See all". The action is shown only when both [actionLabel] and
 * [onActionClick] are provided; the row fills the width only in that case.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    val hasAction = actionLabel != null && onActionClick != null
    Row(
        // Only stretch when there is an action to push to the end; otherwise the header stays
        // wrap-content so it can sit inside a parent Row next to other content.
        modifier = if (hasAction) modifier.fillMaxWidth() else modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(Locale.current.platformLocale),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.semantics { heading() },
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) { Text(actionLabel) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionHeaderPreview() {
    NotificappTheme(dynamicColor = false) {
        SectionHeader(title = "Recent activity", actionLabel = "See all", onActionClick = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SectionHeaderPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        SectionHeader(title = "Last 7 days")
    }
}
