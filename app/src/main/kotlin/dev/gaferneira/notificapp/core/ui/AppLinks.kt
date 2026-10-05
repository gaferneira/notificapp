package dev.gaferneira.notificapp.core.ui

/**
 * Canonical external links surfaced from the UI. Centralized so the privacy statement (linked from
 * both onboarding and Settings) has a single source of truth. Points at the repository-hosted
 * `PRIVACY.md`, keeping the app itself asset-free and always current with the published policy.
 */
object AppLinks {
    const val PRIVACY_POLICY_URL = "https://github.com/gaferneira/notificapp/blob/main/PRIVACY.md"
    const val OPEN_SOURCE_LICENSES_URL = "https://github.com/gaferneira/notificapp/blob/main/THIRD_PARTY_LICENSES.md"
}
