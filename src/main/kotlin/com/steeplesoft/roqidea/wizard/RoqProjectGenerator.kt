package com.steeplesoft.roqidea.wizard

/**
 * Common interface for Roq project generators.
 * Implemented by both Codestart API and CreateProject command generators.
 */
interface RoqProjectGenerator {
    /**
     * Creates a new Roq project using the provided configuration.
     *
     * @param config Project configuration
     */
    fun createRoqProject(config: RoqProjectConfig)
}
