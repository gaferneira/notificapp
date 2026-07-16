package dev.gaferneira.notificapp.core.rulesharing

/**
 * Metadata for a single starter rule template, shipped as a JSON asset under
 * `app/src/main/assets/rules/`. The actual [RuleExportDto][dev.gaferneira.notificapp.core.rulesharing.dto.RuleExportDto]
 * JSON is only read (from `assets/rules/$assetFileName`) when the user picks a template - this
 * registry exists so the template picker UI can render the list without parsing every asset just
 * to show a label.
 */
data class RuleTemplateInfo(
    val assetFileName: String,
    val name: String,
    val description: String,
    val category: String,
)

/**
 * Curated starter rule templates, one per hero use case, covering every [ActionType][dev.gaferneira.notificapp.domain.model.ActionType].
 * Templates ride the same import pipeline as Phase 2 file/clipboard import: reading the asset text
 * and feeding it through `RuleJsonCodec.decode` behaves identically to any other imported rule
 * (fresh IDs, forced dry-run, on confirmation).
 */
object RuleTemplates {
    val all: List<RuleTemplateInfo> = listOf(
        RuleTemplateInfo(
            assetFileName = "bank-payment-tracker.json",
            name = "Bank payment tracker",
            description = "Extracts the amount and date from your bank's payment notifications into a searchable dataset.",
            category = "Finance",
        ),
        RuleTemplateInfo(
            assetFileName = "package-delivery-tracker.json",
            name = "Package delivery tracker",
            description = "Catches delivery notifications and pulls out the tracking number so every package ends up in one searchable place.",
            category = "Deliveries",
        ),
        RuleTemplateInfo(
            assetFileName = "mute-promotions.json",
            name = "Mute promotional notifications",
            description = "Automatically dismisses notifications that look like sales or promotions.",
            category = "Noise control",
        ),
        RuleTemplateInfo(
            assetFileName = "snooze-digests.json",
            name = "Snooze newsletters and digests",
            description = "Holds back newsletter and digest notifications for 4 hours so they arrive in a batch.",
            category = "Noise control",
        ),
        RuleTemplateInfo(
            assetFileName = "flash-verification-codes.json",
            name = "Flash alert for verification codes",
            description = "Flashes the camera torch when a one-time verification code arrives.",
            category = "Security",
        ),
        RuleTemplateInfo(
            assetFileName = "wake-for-urgent-alerts.json",
            name = "Wake me for urgent alerts",
            description = "Rings a full alarm when a notification is marked urgent or an emergency, even on silent.",
            category = "Alerts",
        ),
    )
}
