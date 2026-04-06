package com.steeplesoft.roqidea.wizard

import io.quarkus.devtools.project.BuildTool
import java.nio.file.Path

/**
 * Common configuration for creating Roq projects.
 * Used by both Codestart API and CreateProject command generators.
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
 * @property additionalPlugins Set of additional Roq plugin names to include
 */
data class RoqProjectConfig(
    val outputPath: Path,
    val groupId: String = "com.example",
    val artifactId: String = "my-roq-site",
    val version: String = "1.0.0-SNAPSHOT",
    val buildTool: BuildTool = BuildTool.MAVEN,
    val javaVersion: String = "21",
    val quarkusVersion: String = "3.34.1",
    val roqVersion: String = "2.0.5",
    val includeDefaultTheme: Boolean = true,
    val additionalPlugins: Set<String> = emptySet()
)
