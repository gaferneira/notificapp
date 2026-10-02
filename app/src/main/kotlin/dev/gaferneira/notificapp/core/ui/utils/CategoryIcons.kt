package dev.gaferneira.notificapp.core.ui.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps a free-text rule/template category label to a representative icon. */
fun getCategoryIcon(category: String): ImageVector = when (category.lowercase()) {
    "finance", "financial", "banking", "payments" -> Icons.Outlined.AccountBalance
    "deliveries", "delivery", "shipping", "logistics" -> Icons.Outlined.LocalShipping
    "shopping", "e-commerce", "retail" -> Icons.Outlined.ShoppingCart
    "receipts", "transactions", "purchases" -> Icons.Outlined.Receipt
    "payments", "invoices" -> Icons.Outlined.Payments
    "noise control" -> Icons.Outlined.NotificationsOff
    "security" -> Icons.Outlined.Security
    "alerts" -> Icons.Outlined.NotificationImportant
    else -> Icons.Outlined.Receipt
}
