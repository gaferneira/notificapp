package dev.gaferneira.notificapp.core.rulesharing.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire representation of the `AppInfo` domain model.
 */
@Serializable
data class AppInfoDto(
    @SerialName("packageName") val packageName: String,
    @SerialName("name") val name: String,
    @SerialName("category") val category: String? = null,
)
