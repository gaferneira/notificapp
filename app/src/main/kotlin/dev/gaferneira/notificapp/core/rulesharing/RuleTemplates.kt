package dev.gaferneira.notificapp.core.rulesharing

import androidx.annotation.StringRes
import dev.gaferneira.notificapp.R

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
    /** English category; doubles as the stable filter key (the label shown is [categoryRes]). */
    val category: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val categoryRes: Int,
)

/**
 * Curated starter rule templates, one per hero use case, covering every [ActionType][dev.gaferneira.notificapp.domain.model.ActionType].
 * Templates ride the same decode pipeline as Phase 2 file/clipboard import (fresh IDs), but unlike
 * imported files they are bundled and trusted: the editor keeps each template's own `isDryRun` flag
 * (false in every bundled asset) instead of forcing dry-run.
 */
object RuleTemplates {
    val all: List<RuleTemplateInfo> = listOf(
        RuleTemplateInfo(
            assetFileName = "bank-payment-tracker.json",
            name = "Bank payment tracker",
            description = "Extracts the amount and date from your bank's payment notifications into a searchable dataset.",
            category = "Finance",
            nameRes = R.string.template_bank_payment_name,
            descriptionRes = R.string.template_bank_payment_description,
            categoryRes = R.string.template_category_finance,
        ),
        RuleTemplateInfo(
            assetFileName = "package-delivery-tracker.json",
            name = "Package delivery tracker",
            description = "Catches delivery notifications and pulls out the tracking number so every package ends up in one searchable place.",
            category = "Deliveries",
            nameRes = R.string.template_package_delivery_name,
            descriptionRes = R.string.template_package_delivery_description,
            categoryRes = R.string.template_category_deliveries,
        ),
        RuleTemplateInfo(
            assetFileName = "mute-promotions.json",
            name = "Mute promotional notifications",
            description = "Automatically dismisses notifications that look like sales or promotions.",
            category = "Noise control",
            nameRes = R.string.template_mute_promotions_name,
            descriptionRes = R.string.template_mute_promotions_description,
            categoryRes = R.string.template_category_noise,
        ),
        RuleTemplateInfo(
            assetFileName = "snooze-digests.json",
            name = "Snooze newsletters and digests",
            description = "Holds back newsletter and digest notifications for 4 hours so they arrive in a batch.",
            category = "Noise control",
            nameRes = R.string.template_snooze_digests_name,
            descriptionRes = R.string.template_snooze_digests_description,
            categoryRes = R.string.template_category_noise,
        ),
        RuleTemplateInfo(
            assetFileName = "flash-verification-codes.json",
            name = "Flash alert for verification codes",
            description = "Flashes the camera torch when a one-time verification code arrives.",
            category = "Security",
            nameRes = R.string.template_flash_codes_name,
            descriptionRes = R.string.template_flash_codes_description,
            categoryRes = R.string.template_category_security,
        ),
        RuleTemplateInfo(
            assetFileName = "wake-for-urgent-alerts.json",
            name = "Wake me for urgent alerts",
            description = "Rings a full alarm when a notification is marked urgent or an emergency, even on silent.",
            category = "Alerts",
            nameRes = R.string.template_urgent_alerts_name,
            descriptionRes = R.string.template_urgent_alerts_description,
            categoryRes = R.string.template_category_alerts,
        ),
    )

    fun find(assetFileName: String): RuleTemplateInfo? = all.firstOrNull { it.assetFileName == assetFileName }

    /** Localized label resource for an English [category] key from [all]. */
    @StringRes
    fun categoryLabelRes(category: String): Int? = all.firstOrNull { it.category == category }?.categoryRes
}
