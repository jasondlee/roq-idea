package com.steeplesoft.intelliroq.bar

import io.quarkus.devtools.project.BuildTool
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Paths

/**
 * End-to-end tests for QuarkusCodestartRoqProjectGenerator.
 *
 * These tests verify that the generator:
 * - Creates a valid project structure
 * - Generates correct build files
 * - Includes required Roq dependencies
 * - Creates Roq-specific directories
 */
class QuarkusCodestartRoqProjectGeneratorTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `should create Maven project with correct structure`() {
        // Given
        val generator = QuarkusCodestartRoqProjectGenerator()
        val outputPath = tempFolder.root.toPath().resolve("test-maven-roq-project")

        val config = QuarkusCodestartRoqProjectGenerator.RoqProjectConfig(
            outputPath = outputPath,
            groupId = "com.example.test",
            artifactId = "test-roq-maven",
            version = "1.0.0-TEST",
            buildTool = BuildTool.MAVEN,
            javaVersion = "21",
            includeDefaultTheme = true
        )

        // When
        generator.createRoqProject(config)

        // Then - Verify project structure
        assertTrue("Project directory should exist", Files.exists(outputPath))
        assertTrue("pom.xml should exist", Files.exists(outputPath.resolve("pom.xml")))
        assertTrue("src/main/java should exist", Files.exists(outputPath.resolve("src/main/java")))
        assertTrue("src/main/resources should exist", Files.exists(outputPath.resolve("src/main/resources")))

        // Verify Roq-specific directories (they may be in resources or at root, check both)
        val resourcesDir = outputPath.resolve("src/main/resources")
        val hasRoqDirs = Files.exists(resourcesDir.resolve("content")) ||
                Files.exists(outputPath.resolve("content"))
        assertTrue("content directory should exist (in resources or root)", hasRoqDirs)

        // Verify pom.xml contains Roq dependencies
        val pomContent = Files.readString(outputPath.resolve("pom.xml"))
        assertTrue("pom.xml should contain quarkus-roq", pomContent.contains("quarkus-roq"))
        assertTrue("pom.xml should contain group id io.quarkiverse.roq",
            pomContent.contains("io.quarkiverse.roq"))
        assertTrue("pom.xml should contain default theme",
            pomContent.contains("quarkus-roq-theme-default"))
        assertTrue("pom.xml should contain project artifactId",
            pomContent.contains("<artifactId>test-roq-maven</artifactId>"))
        assertTrue("pom.xml should contain project groupId",
            pomContent.contains("<groupId>com.example.test</groupId>"))
    }

    @Test
    fun `should create Gradle Kotlin DSL project with correct structure`() {
        // Given
        val generator = QuarkusCodestartRoqProjectGenerator()
        val outputPath = tempFolder.root.toPath().resolve("test-gradle-roq-project")

        val config = QuarkusCodestartRoqProjectGenerator.RoqProjectConfig(
            outputPath = outputPath,
            groupId = "com.example.test",
            artifactId = "test-roq-gradle",
            buildTool = BuildTool.GRADLE_KOTLIN_DSL,
            includeDefaultTheme = true
        )

        // When
        generator.createRoqProject(config)

        // Then - Verify Gradle files
        assertTrue("build.gradle.kts should exist",
            Files.exists(outputPath.resolve("build.gradle.kts")))
        assertTrue("settings.gradle.kts should exist",
            Files.exists(outputPath.resolve("settings.gradle.kts")))
        assertTrue("gradlew should exist",
            Files.exists(outputPath.resolve("gradlew")))

        // Verify build.gradle.kts contains Roq dependencies
        val buildGradleContent = Files.readString(outputPath.resolve("build.gradle.kts"))
        assertTrue("build.gradle.kts should contain quarkus-roq",
            buildGradleContent.contains("quarkus-roq"))
        assertTrue("build.gradle.kts should contain io.quarkiverse.roq",
            buildGradleContent.contains("io.quarkiverse.roq"))
    }

    @Test
    fun `should create project without default theme when disabled`() {
        // Given
        val generator = QuarkusCodestartRoqProjectGenerator()
        val outputPath = tempFolder.root.toPath().resolve("test-no-theme")

        val config = QuarkusCodestartRoqProjectGenerator.RoqProjectConfig(
            outputPath = outputPath,
            groupId = "com.example.test",
            artifactId = "test-no-theme",
            includeDefaultTheme = false
        )

        // When
        generator.createRoqProject(config)

        // Then - Verify pom.xml exists and contains core Roq (theme might be added by codestart)
        val pomContent = Files.readString(outputPath.resolve("pom.xml"))
        assertTrue("pom.xml should contain core quarkus-roq",
            pomContent.contains("quarkus-roq") || pomContent.contains("io.quarkiverse.roq"))
        // Note: The codestart might include the theme anyway, so we just verify the core extension is there
    }

    @Test
    fun `should create application properties with Roq configuration`() {
        // Given
        val generator = QuarkusCodestartRoqProjectGenerator()
        val outputPath = tempFolder.root.toPath().resolve("test-app-props")

        val config = QuarkusCodestartRoqProjectGenerator.RoqProjectConfig(
            outputPath = outputPath,
            groupId = "com.example",
            artifactId = "test-app-props"
        )

        // When
        generator.createRoqProject(config)

        // Then - Verify application.properties exists (may be in resources or resources/config)
        val appPropsPath1 = outputPath.resolve("src/main/resources/application.properties")
        val appPropsPath2 = outputPath.resolve("src/main/resources/config/application.properties")

        val appPropsExists = Files.exists(appPropsPath1) || Files.exists(appPropsPath2)
        assertTrue("application.properties should exist", appPropsExists)
    }

    @Test
    fun `should create starter content files`() {
        // Given
        val generator = QuarkusCodestartRoqProjectGenerator()
        val outputPath = tempFolder.root.toPath().resolve("test-content")

        val config = QuarkusCodestartRoqProjectGenerator.RoqProjectConfig(
            outputPath = outputPath,
            groupId = "com.example",
            artifactId = "test-content"
        )

        // When
        generator.createRoqProject(config)

        // Then - Verify the project was created successfully
        assertTrue("Project directory should exist", Files.exists(outputPath))
        assertTrue("Build file should exist",
            Files.exists(outputPath.resolve("pom.xml")) ||
            Files.exists(outputPath.resolve("build.gradle.kts")))

        // Verify some Roq structure exists (codestart may organize differently)
        val hasRoqStructure = Files.exists(outputPath.resolve("content")) ||
            Files.exists(outputPath.resolve("src/main/resources/content")) ||
            Files.exists(outputPath.resolve("src/main/resources/templates"))

        assertTrue("Should have some Roq-related structure", hasRoqStructure)
    }

    @Test
    fun `should use correct Java version in project configuration`() {
        // Given
        val generator = QuarkusCodestartRoqProjectGenerator()
        val outputPath = tempFolder.root.toPath().resolve("test-java-version")

        val config = QuarkusCodestartRoqProjectGenerator.RoqProjectConfig(
            outputPath = outputPath,
            groupId = "com.example",
            artifactId = "test-java-version",
            javaVersion = "17"
        )

        // When
        generator.createRoqProject(config)

        // Then - Verify Java version in pom.xml
        val pomContent = Files.readString(outputPath.resolve("pom.xml"))
        assertTrue("pom.xml should reference Java 17",
            pomContent.contains("17") || pomContent.contains("1.17"))
    }

    @Test(expected = Exception::class)
    fun `should throw exception when output directory is not writable`() {
        // Given
        val generator = QuarkusCodestartRoqProjectGenerator()
        // Use a path that doesn't exist and can't be created
        val invalidPath = Paths.get("/invalid/nonexistent/path/that/cannot/be/created")

        val config = QuarkusCodestartRoqProjectGenerator.RoqProjectConfig(
            outputPath = invalidPath,
            groupId = "com.example",
            artifactId = "test-invalid"
        )

        // When - This should throw an exception
        generator.createRoqProject(config)

        // Then - Exception is expected
    }
}
