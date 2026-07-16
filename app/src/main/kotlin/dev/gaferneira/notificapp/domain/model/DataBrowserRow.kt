package dev.gaferneira.notificapp.domain.model

/**
 * Flat, joined projection of one extracted value for the Data Browser: [ExtractedFieldValue]
 * joined with its owning [RuleField] (name/type), [RuleExecution] (timestamp), [Rule] (name), and
 * source [Notification] (app + summary). This is the read model the browse/search/export surfaces
 * consume directly - no separate mapper, since `DataBrowserDao` selects straight into this shape.
 *
 * @property valueId The underlying `ExtractedFieldValue.id`
 * @property executionId The owning `RuleExecution.id`
 * @property ruleName Name of the rule that produced this value
 * @property packageName Source app's package name
 * @property appName Source app's display name
 * @property fieldName Name of the extraction field, from `RuleField`
 * @property fieldType Type of the extraction field, from `RuleField`
 * @property valueText String value, populated for STRING/CURRENCY fields
 * @property valueNumber Numeric value, populated for NUMBER fields
 * @property valueDate Timestamp value, populated for DATE fields
 * @property notificationTitle Title of the source notification, if any
 * @property notificationContent Content of the source notification, if any
 * @property createdAt When the owning rule execution occurred (epoch millis)
 */
data class DataBrowserRow(
    val valueId: String,
    val executionId: String,
    val ruleName: String,
    val packageName: String,
    val appName: String,
    val fieldName: String,
    val fieldType: RuleField.FieldType,
    val valueText: String?,
    val valueNumber: Double?,
    val valueDate: Long?,
    val notificationTitle: String?,
    val notificationContent: String?,
    val createdAt: Long,
)

/**
 * Combinable browse/search/export/delete filter for the Data Browser. Every field is optional
 * (empty list / null / blank means "no filter on this dimension"), mirroring the
 * `IN (:x) OR :hasFilter = 0` idiom already used by `NotificationDao.getFilteredPaged`.
 *
 * @property ruleIds Restrict to these rule IDs, empty = all rules
 * @property packageNames Restrict to these source app package names, empty = all apps
 * @property fieldTypes Restrict to these field types, empty = all types
 * @property dateFrom Restrict to executions at/after this epoch-millis timestamp, null = no lower bound
 * @property dateTo Restrict to executions at/before this epoch-millis timestamp, null = no upper bound
 * @property searchQuery Free-text search over `value_text`, blank = no search
 * @property sort Result ordering
 */
data class DataBrowserFilter(
    val ruleIds: List<String> = emptyList(),
    val packageNames: List<String> = emptyList(),
    val fieldTypes: List<RuleField.FieldType> = emptyList(),
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val searchQuery: String = "",
    val sort: DataSort = DataSort.DATE_DESC,
)

/**
 * Sort key + direction for Data Browser results.
 *
 * The ordinal of each entry is used as a `sortKey` bound parameter in `DataBrowserDao`'s dynamic
 * `CASE`-based `ORDER BY` (Room query parameters can't be used as raw SQL identifiers), so entries
 * MUST NOT be reordered - append new entries at the end only.
 */
enum class DataSort {
    DATE_DESC,
    DATE_ASC,
    RULE_ASC,
    RULE_DESC,
    APP_ASC,
    APP_DESC,
    FIELD_ASC,
    FIELD_DESC,
}

/** One labeled count, e.g. a single rule's or app's extraction count. */
data class CountBucket(val label: String, val count: Int)

/** One calendar-day bucket in a trend series, [date] formatted `YYYY-MM-DD` in local time. */
data class TrendPoint(val date: String, val count: Int)

/**
 * Aggregate statistics over the (optionally filtered) extracted data set.
 *
 * @property total Total matching extraction count
 * @property thisWeek Matching extraction count within the current local calendar week
 * @property mostActiveRuleName Name of the rule with the most matching extractions, null if there is no data
 * @property perRule Matching extraction counts grouped by rule, most active first
 * @property perApp Matching extraction counts grouped by source app, most active first
 * @property trend Day-bucketed matching extraction counts, oldest first; zero-count days are included
 */
data class DataStatistics(
    val total: Int,
    val thisWeek: Int,
    val mostActiveRuleName: String?,
    val perRule: List<CountBucket>,
    val perApp: List<CountBucket>,
    val trend: List<TrendPoint>,
)

/** On-demand export file format for the Data Browser. */
enum class ExportFormat {
    CSV,
    JSON,
}
