package com.steeplesoft.intelliroq.bar

import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartCatalog
import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartData.QuarkusDataKey
import io.quarkus.devtools.codestarts.quarkus.QuarkusCodestartProjectInput
import io.quarkus.devtools.messagewriter.MessageWriter
import io.quarkus.devtools.project.BuildTool
import io.quarkus.devtools.project.CodestartResourceLoadersBuilder
import io.quarkus.devtools.project.QuarkusProjectHelper
import io.quarkus.maven.dependency.ArtifactCoords
import io.quarkus.registry.catalog.ExtensionCatalog
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Production example demonstrating the Quarkus Codestart API for creating Roq projects.
 *
 * This class shows how to use the official Quarkus devtools API instead of manually
 * generating build files and directory structures. It leverages the same codestart
 * infrastructure that code.quarkus.io uses.
 *
 * ## Usage Example
 * ```kotlin
 * val generator = QuarkusCodestartRoqProjectGenerator()
 * val config = RoqProjectConfig(
 *     outputPath = Paths.get("/tmp/my-roq-site"),
 *     groupId = "com.example",
 *     artifactId = "my-roq-site"
 * )
 * generator.createRoqProject(config)
 * ```
 *
 * ## Comparison to Manual Approach
 * The IntelliRoq plugin currently uses [RoqBuildFileGenerator] and [RoqProjectInitializer]
 * to manually create build files and directory structures. This codestart approach:
 * - Uses official Quarkus project generation infrastructure
 * - Automatically handles all build tool variations
 * - Includes proper codestart templates from the quarkus-roq extension
 * - Validates extension compatibility
 * - Generates example content and configuration
 * - Matches the behavior of code.quarkus.io
 *
 * @see <a href="https://github.com/quarkusio/quarkus/tree/main/devtools">Quarkus Devtools</a>
 * @see <a href="https://quarkiverse.github.io/quarkiverse-docs/quarkus-roq/dev/">Roq Documentation</a>
 */
class QuarkusCodestartRoqProjectGenerator {

    companion object {
        /** Default Quarkus platform version */
        const val QUARKUS_VERSION = "3.34.1"

        /** Default Roq extension version */
        const val ROQ_VERSION = "2.0.5"

        /** Maven group ID for Roq extensions */
        const val ROQ_GROUP_ID = "io.quarkiverse.roq"

        /** Core Roq extension artifact ID */
        const val ROQ_EXTENSION_ARTIFACT_ID = "quarkus-roq"

        /** Default Roq theme artifact ID */
        const val ROQ_THEME_DEFAULT_ARTIFACT_ID = "quarkus-roq-theme-default"

        /**
         * Example main method demonstrating usage.
         * Run this to generate a sample Roq project at /tmp/example-roq-site
         */
        @JvmStatic
        fun main(args: Array<String>) {
            val generator = QuarkusCodestartRoqProjectGenerator()
            val config = RoqProjectConfig(
                outputPath = Paths.get("/tmp/example-roq-site"),
                groupId = "com.example",
                artifactId = "example-roq-site",
                buildTool = BuildTool.MAVEN,
                // Include some popular plugins
                additionalPlugins = setOf(
                    RoqPlugins.TAGGING,
                    RoqPlugins.SITEMAP,
                    RoqPlugins.LUNR
                )
            )

            try {
                println("Creating Roq project at: ${config.outputPath}")
                println("Including plugins: ${config.additionalPlugins.joinToString(", ")}")
                generator.createRoqProject(config)
                println("✓ Project created successfully!")
                println("  Navigate to: ${config.outputPath}")
                println("  Run with: ./mvnw quarkus:dev")
            } catch (e: Exception) {
                System.err.println("✗ Error creating project: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Configuration for creating a Roq project.
     *
     * @property outputPath Directory where the project will be created
     * @property groupId Maven/Gradle group ID (e.g., "com.example")
     * @property artifactId Maven/Gradle artifact ID (e.g., "my-roq-site")
     * @property version Project version (e.g., "1.0.0-SNAPSHOT")
     * @property buildTool Build tool to use (MAVEN, GRADLE, or GRADLE_KOTLIN_DSL)
     * @property javaVersion Target Java version (e.g., "21")
     * @property quarkusVersion Quarkus platform version to use
     * @property roqVersion Roq extension version to use
     * @property includeDefaultTheme Whether to include the default Roq theme
     * @property additionalPlugins Set of additional Roq plugin names to include (e.g., "tagging", "sitemap", "lunr")
     */
    data class RoqProjectConfig(
        val outputPath: Path,
        val groupId: String = "com.example",
        val artifactId: String = "my-roq-site",
        val version: String = "1.0.0-SNAPSHOT",
        val buildTool: BuildTool = BuildTool.MAVEN,
        val javaVersion: String = "21",
        val quarkusVersion: String = QUARKUS_VERSION,
        val roqVersion: String = ROQ_VERSION,
        val includeDefaultTheme: Boolean = true,
        val additionalPlugins: Set<String> = emptySet()
    )

    /**
     * Available Roq plugins that can be included in projects.
     * Based on the official Roq plugin ecosystem.
     */
    object RoqPlugins {
        const val TAGGING = "tagging"
        const val ALIASES = "aliases"
        const val ASCIIDOC_JRUBY = "asciidoc-jruby"
        const val ASCIIDOC = "asciidoc"
        const val QRCODE = "qrcode"
        const val SERIES = "series"
        const val SITEMAP = "sitemap"
        const val LUNR = "lunr"
        const val DIAGRAM = "diagram"

        /**
         * All available plugin names.
         */
        val ALL = setOf(
            TAGGING, ALIASES, ASCIIDOC_JRUBY, ASCIIDOC, QRCODE,
            SERIES, SITEMAP, LUNR, DIAGRAM
        )
    }

    /**
     * Creates a new Roq project using the Quarkus Codestart API.
     *
     * This method orchestrates the entire project creation process:
     * 1. Resolves the Quarkus extension catalog
     * 2. Creates a codestart catalog from available extensions
     * 3. Builds project input configuration with Roq-specific settings
     * 4. Generates the complete project structure
     *
     * @param config Project configuration
     * @throws io.quarkus.registry.RegistryResolutionException if extension catalog cannot be resolved
     * @throws io.quarkus.devtools.codestarts.CodestartStructureException if project generation fails
     */
    fun createRoqProject(config: RoqProjectConfig) {
        // Step 1: Resolve extension catalog from Quarkus registry
        val extensionCatalog = resolveExtensionCatalog(config.quarkusVersion)

        // Step 2: Create codestart catalog from extension catalog
        val codestartCatalog = createCodestartCatalog(extensionCatalog)

        // Step 3: Build project input with Roq configuration
        val projectInput = buildProjectInput(config)

        // Step 4: Generate the project to the output directory
        generateProject(codestartCatalog, projectInput, config.outputPath)
    }

    /**
     * Resolves the Quarkus extension catalog for the specified version.
     *
     * The extension catalog contains metadata about all available Quarkus extensions,
     * including their codestarts, dependencies, and compatibility information.
     *
     * @param quarkusVersion Quarkus platform version (not currently used, uses default registry)
     * @return Extension catalog containing all available extensions
     */
    private fun resolveExtensionCatalog(quarkusVersion: String): ExtensionCatalog {
        val catalogResolver = QuarkusProjectHelper.getCatalogResolver()
        return catalogResolver.resolveExtensionCatalog()
    }

    /**
     * Creates a QuarkusCodestartCatalog from the extension catalog.
     *
     * The codestart catalog aggregates all codestarts from the Quarkus platform
     * and registered extensions, making them available for project generation.
     *
     * @param extensionCatalog Extension catalog from registry
     * @return Codestart catalog ready for project generation
     */
    private fun createCodestartCatalog(extensionCatalog: ExtensionCatalog): QuarkusCodestartCatalog {
        // Build resource loaders that include base codestarts (java, kotlin, scala)
        // and extension-specific codestarts
        val resourceLoaders = CodestartResourceLoadersBuilder.codestartLoadersBuilder(
            MessageWriter.info()
        )
            .catalog(extensionCatalog)
            .build()

        return QuarkusCodestartCatalog.fromExtensionsCatalog(
            extensionCatalog,
            resourceLoaders
        )
    }

    /**
     * Builds the QuarkusCodestartProjectInput with Roq-specific configuration.
     *
     * The project input defines:
     * - Project metadata (group ID, artifact ID, version)
     * - Build tool configuration
     * - Language selection (Java, Kotlin, or Scala)
     * - Codestarts to apply (language + extension codestarts)
     * - Extensions to include as dependencies
     *
     * @param config User-provided project configuration
     * @return Configured project input ready for generation
     */
    private fun buildProjectInput(config: RoqProjectConfig): QuarkusCodestartProjectInput {
        // Build list of extensions to include
        val extensions = buildExtensionList(config)

        // Build project data map with metadata
        val projectData = buildProjectData(config)

        return QuarkusCodestartProjectInput.builder()
            // Build configuration
            .buildTool(config.buildTool)

            // Language codestart using the Language enum
            // This provides the base project structure and example code
            .addCodestart(QuarkusCodestartCatalog.Language.JAVA.key())

            // Roq codestart from the quarkus-roq extension
            // This adds Roq-specific starter content and configuration
            .addCodestart("roq")

            // Extensions (dependencies) to include in the build file
            .addExtensions(extensions)

            // Project metadata and configuration
            .addData(projectData)

            .build()
    }

    /**
     * Builds the project data map with metadata for template rendering.
     *
     * This map provides variables used by codestart templates to generate
     * build files and configuration.
     *
     * @param config Project configuration
     * @return Map of data keys to values for template rendering
     */
    private fun buildProjectData(config: RoqProjectConfig): Map<String, Any> {
        val data = mutableMapOf<String, Any>()

        // Project coordinates
        data[QuarkusDataKey.PROJECT_GROUP_ID.key()] = config.groupId
        data[QuarkusDataKey.PROJECT_ARTIFACT_ID.key()] = config.artifactId
        data[QuarkusDataKey.PROJECT_VERSION.key()] = config.version

        // Java version
        data[QuarkusDataKey.JAVA_VERSION.key()] = config.javaVersion

        // BOM coordinates (Quarkus platform BOM)
        data[QuarkusDataKey.BOM_GROUP_ID.key()] = "io.quarkus.platform"
        data[QuarkusDataKey.BOM_ARTIFACT_ID.key()] = "quarkus-bom"
        data[QuarkusDataKey.BOM_VERSION.key()] = config.quarkusVersion

        // Quarkus version
        data[QuarkusDataKey.QUARKUS_VERSION.key()] = config.quarkusVersion

        // Maven plugin coordinates
        data[QuarkusDataKey.QUARKUS_MAVEN_PLUGIN_GROUP_ID.key()] = "io.quarkus.platform"
        data[QuarkusDataKey.QUARKUS_MAVEN_PLUGIN_ARTIFACT_ID.key()] = "quarkus-maven-plugin"
        data[QuarkusDataKey.QUARKUS_MAVEN_PLUGIN_VERSION.key()] = config.quarkusVersion

        // Gradle plugin coordinates
        data[QuarkusDataKey.QUARKUS_GRADLE_PLUGIN_ID.key()] = "io.quarkus"
        data[QuarkusDataKey.QUARKUS_GRADLE_PLUGIN_VERSION.key()] = config.quarkusVersion

        // Maven plugin versions (required by templates)
        data[QuarkusDataKey.MAVEN_COMPILER_PLUGIN_VERSION.key()] = "3.13.0"
        data[QuarkusDataKey.MAVEN_SUREFIRE_PLUGIN_VERSION.key()] = "3.5.2"

        // Language versions (for templates that might reference them)
        data[QuarkusDataKey.KOTLIN_VERSION.key()] = "2.1.0"
        data[QuarkusDataKey.SCALA_VERSION.key()] = "2.12.20"
        data[QuarkusDataKey.SCALA_MAVEN_PLUGIN_VERSION.key()] = "4.9.2"

        return data
    }

    /**
     * Builds the list of Roq extension artifacts to include as dependencies.
     *
     * At minimum, includes the core quarkus-roq extension. Optionally includes:
     * - Default theme (if configured)
     * - Additional Roq plugins (tagging, sitemap, etc.)
     *
     * @param config Project configuration
     * @return List of extension coordinates to add as dependencies
     */
    private fun buildExtensionList(config: RoqProjectConfig): List<ArtifactCoords> {
        val extensions = mutableListOf<ArtifactCoords>()

        // Core Roq extension (required)
        extensions.add(
            ArtifactCoords.jar(
                ROQ_GROUP_ID,
                ROQ_EXTENSION_ARTIFACT_ID,
                config.roqVersion
            )
        )

        // Optional: Default theme
        if (config.includeDefaultTheme) {
            extensions.add(
                ArtifactCoords.jar(
                    ROQ_GROUP_ID,
                    ROQ_THEME_DEFAULT_ARTIFACT_ID,
                    config.roqVersion
                )
            )
        }

        // Additional Roq plugins
        config.additionalPlugins.forEach { pluginName ->
            extensions.add(
                ArtifactCoords.jar(
                    ROQ_GROUP_ID,
                    "quarkus-roq-plugin-$pluginName",
                    config.roqVersion
                )
            )
        }

        return extensions
    }

    /**
     * Generates the project using the codestart catalog and input.
     *
     * This creates the complete project structure including:
     * - Build files (pom.xml or build.gradle)
     * - Source directories (src/main/java, src/main/resources, etc.)
     * - Starter content (content/, data/, templates/ directories)
     * - Configuration files (application.properties)
     * - README and other documentation
     *
     * @param catalog Codestart catalog containing available codestarts
     * @param input Project input with configuration
     * @param outputPath Directory where project will be created
     */
    private fun generateProject(
        catalog: QuarkusCodestartCatalog,
        input: QuarkusCodestartProjectInput,
        outputPath: Path
    ) {
        catalog.createProject(input).generate(outputPath)
    }
}
