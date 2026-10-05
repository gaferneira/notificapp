package dev.gaferneira.notificapp.domain.repository

/**
 * Reads the raw JSON of a bundled starter rule template.
 */
interface RuleTemplateRepository {

    /**
     * Returns the JSON text of the template shipped as [assetFileName], or a failure when the
     * template does not exist or cannot be read.
     */
    suspend fun getTemplateText(assetFileName: String): Result<String>
}
