package com.steeplesoft.intelliroq.wizard

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.steeplesoft.intelliroq.bar.QuarkusCodestartRoqProjectGenerator
import com.steeplesoft.intelliroq.icons.RoqIcons
import io.quarkus.devtools.project.BuildTool
import org.jetbrains.jps.model.java.JavaResourceRootType

/**
 * Module builder for creating new Roq projects via File | New | Project.
 */
class RoqModuleBuilder : ModuleBuilder() {

    enum class BuildSystem {
        MAVEN, GRADLE
    }

    var siteUrl: String = "http://localhost:8080"
    var selectedPlugins: MutableSet<String> = mutableSetOf("markdown")
    var buildSystem: BuildSystem = BuildSystem.MAVEN
    var useCodestartGenerator: Boolean = true

    override fun getModuleType(): ModuleType<*> = RoqModuleType.INSTANCE

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        super.setupRootModel(modifiableRootModel)

        val contentEntry = doAddContentEntry(modifiableRootModel) ?: run {
            thisLogger().error("Failed to create content entry for Roq project")
            return
        }

        val baseDir = contentEntry.file ?: run {
            thisLogger().error("Content entry has no file")
            return
        }

        try {
            if (useCodestartGenerator) {
                // Use the Quarkus Codestart API
                createProjectWithCodestart(baseDir, modifiableRootModel)
            } else {
                // Use the manual generation approach
                createProjectManually(baseDir, contentEntry)
            }

            // Refresh file system
            baseDir.refresh(false, true)
        } catch (ex: Exception) {
            thisLogger().error("Failed to initialize Roq project", ex)
            Messages.showErrorDialog(
                modifiableRootModel.project,
                "Failed to create Roq project: ${ex.message}",
                "Project Creation Error"
            )
        }
    }

    /**
     * Creates the project using the Quarkus Codestart API.
     */
    private fun createProjectWithCodestart(
        baseDir: com.intellij.openapi.vfs.VirtualFile,
        modifiableRootModel: ModifiableRootModel
    ) {
        thisLogger().info("Creating Roq project using Quarkus Codestart API")

        val generator = QuarkusCodestartRoqProjectGenerator()

        // Convert build system
        val quarkusBuildTool = when (buildSystem) {
            BuildSystem.MAVEN -> BuildTool.MAVEN
            BuildSystem.GRADLE -> BuildTool.GRADLE_KOTLIN_DSL
        }

        // Filter out "markdown" from selected plugins as it's core
        val additionalPlugins = selectedPlugins.filter { it != "markdown" }.toSet()

        val config = QuarkusCodestartRoqProjectGenerator.RoqProjectConfig(
            outputPath = baseDir.toNioPath(),
            groupId = "com.example",
            artifactId = baseDir.name,
            version = "1.0.0-SNAPSHOT",
            buildTool = quarkusBuildTool,
            javaVersion = "21",
            includeDefaultTheme = true,
            additionalPlugins = additionalPlugins
        )

        generator.createRoqProject(config)

        // Mark src/main/resources as resource root
        baseDir.refresh(false, true)
        val resourcesDir = baseDir.findFileByRelativePath("src/main/resources")
        if (resourcesDir != null) {
            val contentEntry = modifiableRootModel.contentEntries.firstOrNull()
            contentEntry?.addSourceFolder(resourcesDir, JavaResourceRootType.RESOURCE)
        }
    }

    /**
     * Creates the project using manual generation (original approach).
     */
    private fun createProjectManually(
        baseDir: com.intellij.openapi.vfs.VirtualFile,
        contentEntry: com.intellij.openapi.roots.ContentEntry
    ) {
        thisLogger().info("Creating Roq project using manual generation")

        WriteCommandAction.runWriteCommandAction(null) {
            // 1. Create directory structure
            RoqProjectInitializer.createDirectoryStructure(baseDir)

            // 2. Create sample files
            RoqProjectInitializer.createSampleFiles(baseDir, siteUrl)

            // 3. Create application.properties
            RoqProjectInitializer.createApplicationProperties(baseDir, siteUrl)

            // 4. Generate build file
            when (buildSystem) {
                BuildSystem.MAVEN -> createMavenProject(baseDir)
                BuildSystem.GRADLE -> createGradleProject(baseDir)
            }

            // 5. Mark src/main/resources as resource root
            val resourcesDir = baseDir.findFileByRelativePath("src/main/resources")
            if (resourcesDir != null) {
                contentEntry.addSourceFolder(resourcesDir, JavaResourceRootType.RESOURCE)
            }
        }
    }

    /**
     * Creates Maven build files.
     */
    private fun createMavenProject(baseDir: com.intellij.openapi.vfs.VirtualFile) {
        val projectName = baseDir.name
        val pomContent = RoqBuildFileGenerator.generateMavenPom(
            projectName = projectName,
            selectedPlugins = selectedPlugins
        )

        val pomFile = baseDir.createChildData(this, "pom.xml")
        pomFile.setBinaryContent(pomContent.toByteArray())
    }

    /**
     * Creates Gradle build files.
     */
    private fun createGradleProject(baseDir: com.intellij.openapi.vfs.VirtualFile) {
        val projectName = baseDir.name

        // Create build.gradle.kts
        val buildContent = RoqBuildFileGenerator.generateGradleBuild(selectedPlugins = selectedPlugins)
        val buildFile = baseDir.createChildData(this, "build.gradle.kts")
        buildFile.setBinaryContent(buildContent.toByteArray())

        // Create settings.gradle.kts
        val settingsContent = RoqBuildFileGenerator.generateGradleSettings(projectName = projectName)
        val settingsFile = baseDir.createChildData(this, "settings.gradle.kts")
        settingsFile.setBinaryContent(settingsContent.toByteArray())

        // Create gradle.properties
        val propertiesContent = RoqBuildFileGenerator.generateGradleProperties()
        val propertiesFile = baseDir.createChildData(this, "gradle.properties")
        propertiesFile.setBinaryContent(propertiesContent.toByteArray())
    }

    override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep {
        return RoqProjectWizardStep(this)
    }

    override fun getBuilderId(): String = "roq.project"

    override fun getPresentableName(): String = "Roq Static Site"

    override fun getDescription(): String = "Create a new Roq static site generator project"

    override fun getNodeIcon() = RoqIcons.RoqProject

    override fun getGroupName(): String = "Quarkus"

    override fun getWeight(): Int = 50
}
