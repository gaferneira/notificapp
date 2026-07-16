package dev.gaferneira.notificapp.domain.model.preferences

/**
 * Enum representing how long captured notifications are retained before being
 * automatically deleted.
 */
enum class RetentionPeriod {
    /** Delete notifications older than 30 days */
    DAYS_30,

    /** Delete notifications older than 90 days */
    DAYS_90,

    /** Never automatically delete notifications */
    NEVER,
}
