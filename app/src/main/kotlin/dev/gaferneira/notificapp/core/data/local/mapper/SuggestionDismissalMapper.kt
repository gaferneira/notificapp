package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.SuggestionDismissalEntity
import dev.gaferneira.notificapp.domain.model.SuggestionDismissalKey

/**
 * Mapper functions for converting between SuggestionDismissalKey domain models and
 * SuggestionDismissalEntity database models.
 */
internal object SuggestionDismissalMapper {

    /**
     * Convert a SuggestionDismissalKey domain model to a SuggestionDismissalEntity.
     *
     * @param domain The domain model
     * @param dismissedAt When the dismissal is recorded (epoch millis)
     * @return The database entity
     */
    fun toEntity(domain: SuggestionDismissalKey, dismissedAt: Long): SuggestionDismissalEntity = SuggestionDismissalEntity(
        packageName = domain.packageName,
        normalizedTitleKey = domain.normalizedTitleKey,
        dismissedAt = dismissedAt,
    )

    /**
     * Convert a SuggestionDismissalEntity to a SuggestionDismissalKey domain model.
     *
     * @param entity The database entity
     * @return The domain model
     */
    fun toDomain(entity: SuggestionDismissalEntity): SuggestionDismissalKey = SuggestionDismissalKey(
        packageName = entity.packageName,
        normalizedTitleKey = entity.normalizedTitleKey,
    )

    /**
     * Convert a list of SuggestionDismissalEntity to a set of domain models, for the repository's
     * `observeDismissals(): Flow<Set<SuggestionDismissalKey>>`.
     *
     * @param entities The database entities
     * @return The domain models, as a Set
     */
    fun toDomainSet(entities: List<SuggestionDismissalEntity>): Set<SuggestionDismissalKey> = entities.map { toDomain(it) }.toSet()
}
