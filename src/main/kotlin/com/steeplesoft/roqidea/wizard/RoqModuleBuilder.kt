package com.steeplesoft.roqidea.wizard

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.steeplesoft.roqidea.bar.QuarkusCodestartRoqProjectGenerator
import com.steeplesoft.roqidea.bar.QuarkusCreateProjectRoqGenerator
import com.steeplesoft.roqidea.icons.RoqIcons
import io.quarkus.devtools.project.BuildTool
import okio.Path.Companion.toPath
import org.jetbrains.jps.model.java.JavaResourceRootType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.isDirectory
import kotlin.io.path.deleteExisting

/**
 * Module builder for creating new Roq projects via File | New | Project.
 */
class RoqModuleBuilder : ModuleBuilder() {

    enum class BuildSystem {
        MAVEN, GRADLE
    }

    enum class GenerationMode {
        CODESTART_API,
        CREATE_PROJECT_COMMAND
    }

    var siteUrl: String = "http://localhost:8080"
    var selectedPlugins: MutableSet<String> = mutableSetOf("markdown")
    var buildSystem: BuildSystem = BuildSystem.MAVEN
    var generationMode: GenerationMode = GenerationMode.CREATE_PROJECT_COMMAND

    override fun getModuleType(): ModuleType<*> = RoqModuleType.INSTANCE

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        super.setupRootModel(modifiableRootModel)

        try {
            createProject(modifiableRootModel)

            val contentEntry = doAddContentEntry(modifiableRootModel) ?: run {
                thisLogger().error("Failed to create content entry for Roq project")
                return
            }

            val baseDir = contentEntry.file ?: run {
                thisLogger().error("Content entry has no file")
                return
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
     * Common project creation logic used by both Codestart API and CreateProject command approaches.
     *
     * @param modifiableRootModel The root model for adding source folders
     */
    private fun createProject(modifiableRootModel: ModifiableRootModel, ) {
        val generator : RoqProjectGenerator = when (generationMode) {
            GenerationMode.CODESTART_API -> {
                // Use the Quarkus Codestart API
                thisLogger().info("Creating Roq project using Quarkus Codestart API")
                QuarkusCodestartRoqProjectGenerator()
            }
            GenerationMode.CREATE_PROJECT_COMMAND -> {
                // Use the CreateProject command API
                thisLogger().info("Creating Roq project using CreateProject command API")
                QuarkusCreateProjectRoqGenerator()
            }
        }

        val baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(contentEntryPath!!.replace('\\', '/'))!!
        // Convert build system
        val quarkusBuildTool = when (buildSystem) {
            BuildSystem.MAVEN -> BuildTool.MAVEN
            BuildSystem.GRADLE -> BuildTool.GRADLE_KOTLIN_DSL
        }

        // Filter out "markdown" from selected plugins as it's core
        val additionalPlugins = selectedPlugins.filter { it != "markdown" }.toSet()

        // Create the project configuration
        val config = RoqProjectConfig(
            outputPath = baseDir.toNioPath(),
            groupId = "com.example",
            artifactId = baseDir.name,
            version = "1.0.0-SNAPSHOT",
            buildTool = quarkusBuildTool,
            javaVersion = "21",
            includeDefaultTheme = true,
            additionalPlugins = additionalPlugins
        )

        // Gross
        moveProjectFiles(baseDir, true);
        // Generate the project
        generator.createRoqProject(config)
        moveProjectFiles(baseDir, false);

        // Mark src/main/resources as resource root
        baseDir.refresh(false, true)
        val resourcesDir = baseDir.findFileByRelativePath("src/main/resources")
        if (resourcesDir != null) {
            val contentEntry = modifiableRootModel.contentEntries.firstOrNull()
            contentEntry?.addSourceFolder(resourcesDir, JavaResourceRootType.RESOURCE)
        }
    }

    private fun moveProjectFiles(baseDir: com.intellij.openapi.vfs.VirtualFile, backup: Boolean) {
        val tmpDir = System.getProperty("java.io.tmpdir")
        var srcDir = Path.of(baseDir.canonicalPath!!)
        var destDir = Path.of(tmpDir, baseDir.name + ".tmp")
        if (!backup) {
            val temp = srcDir
            srcDir = destDir
            destDir = temp
        }

        // Create destination directory if it doesn't exist
        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir)
        }

        // Move all files and directories from srcDir to destDir
        srcDir.listDirectoryEntries().forEach { entry ->
            val target = destDir.resolve(entry.fileName)
            Files.move(entry, target, StandardCopyOption.REPLACE_EXISTING)
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
