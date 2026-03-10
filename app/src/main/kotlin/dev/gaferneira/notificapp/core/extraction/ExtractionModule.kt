package dev.gaferneira.notificapp.core.extraction

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dagger Hilt module for the extraction layer.
 *
 * Provides RuleEngine and related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractionModule {
    // RuleEngine is constructor-injected, no additional bindings needed
    // This module serves as a marker and can be extended if needed
}
