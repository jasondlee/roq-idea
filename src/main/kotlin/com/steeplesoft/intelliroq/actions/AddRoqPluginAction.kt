package com.steeplesoft.intelliroq.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.steeplesoft.intelliroq.services.RoqPluginManager
import com.steeplesoft.intelliroq.services.RoqProjectDetector
import java.io.IOException

/**
 * Action to add a Roq plugin to the project.
 */
class AddRoqPluginAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val detector = project.service<RoqProjectDetector>()

        // Ensure it's a Roq project
        if (!detector.isRoqProject()) {
            Messages.showWarningDialog(
                project,
                "This is not a Roq project. Please initialize Roq structure first.",
                "Not a Roq Project"
            )
            return
        }

        val pluginManager = project.service<RoqPluginManager>()

        // Get available uninstalled plugins
        val availablePlugins = pluginManager.getAvailableUninstalledPlugins()

        if (availablePlugins.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "All known Roq plugins are already installed in this project.",
                "All Plugins Installed"
            )
            return
        }

        // Show selection dialog
        val pluginNames = availablePlugins.map { "${it.displayName} - ${it.description}" }.toTypedArray()
        val selection = Messages.showChooseDialog(
            project,
            "Select a Roq plugin to add to your project:",
            "Add Roq Plugin",
            Messages.getQuestionIcon(),
            pluginNames,
            pluginNames[0]
        )

        if (selection < 0 || selection >= availablePlugins.size) {
            return // User cancelled
        }

        val selectedPlugin = availablePlugins[selection]

        // Determine build system and add plugin
        try {
            val added = addPluginToProject(project, pluginManager, selectedPlugin)

            if (added) {
                Messages.showInfoMessage(
                    project,
                    "Roq plugin '${selectedPlugin.displayName}' has been added to your build file.\n\n" +
                    "Please refresh/sync your project dependencies to apply the changes.",
                    "Plugin Added"
                )
            } else {
                Messages.showErrorDialog(
                    project,
                    "Could not find a suitable build file (pom.xml or build.gradle) to add the plugin.",
                    "Build File Not Found"
                )
            }
        } catch (ex: Exception) {
            thisLogger().error("Failed to add Roq plugin", ex)
            Messages.showErrorDialog(
                project,
                "Failed to add plugin: ${ex.message}",
                "Error Adding Plugin"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }

        val detector = project.service<RoqProjectDetector>()
        e.presentation.isEnabled = detector.isRoqProject()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    /**
     * Adds a plugin to the project's build file.
     */
    private fun addPluginToProject(
        project: Project,
        pluginManager: RoqPluginManager,
        plugin: RoqPluginManager.RoqPlugin
    ): Boolean {
        // Try Maven first
        if (addToMaven(project, pluginManager, plugin)) {
            return true
        }

        // Try Gradle
        if (addToGradle(project, pluginManager, plugin)) {
            return true
        }

        return false
    }

    /**
     * Adds plugin to Maven pom.xml.
     */
    private fun addToMaven(
        project: Project,
        pluginManager: RoqPluginManager,
        plugin: RoqPluginManager.RoqPlugin
    ): Boolean {
        val pomFiles = FilenameIndex.getFilesByName(
            project,
            "pom.xml",
            GlobalSearchScope.projectScope(project)
        )

        if (pomFiles.isEmpty()) return false

        val pomFile = pomFiles.first().virtualFile
        val version = pluginManager.getRoqVersion() ?: RoqPluginManager.DEFAULT_ROQ_VERSION

        try {
            WriteCommandAction.runWriteCommandAction(project) {
                val content = String(pomFile.contentsToByteArray())

                // Find the dependencies section
                val dependencyTag = """
                    <dependency>
                        <groupId>${RoqPluginManager.ROQ_GROUP_ID}</groupId>
                        <artifactId>${plugin.artifactId}</artifactId>
                        <version>$version</version>
                    </dependency>
                """.trimIndent()

                val updatedContent = if (content.contains("</dependencies>")) {
                    // Add before closing dependencies tag
                    content.replace(
                        "</dependencies>",
                        "    $dependencyTag\n    </dependencies>"
                    )
                } else {
                    // Add dependencies section after project tag
                    content.replace(
                        "</project>",
                        """
                        <dependencies>
                            $dependencyTag
                        </dependencies>
                        </project>
                        """.trimIndent()
                    )
                }

                pomFile.setBinaryContent(updatedContent.toByteArray())
                pomFile.refresh(false, false)
            }
            return true
        } catch (e: IOException) {
            thisLogger().error("Failed to update pom.xml", e)
            return false
        }
    }

    /**
     * Adds plugin to Gradle build file.
     */
    private fun addToGradle(
        project: Project,
        pluginManager: RoqPluginManager,
        plugin: RoqPluginManager.RoqPlugin
    ): Boolean {
        val buildFiles = listOf("build.gradle.kts", "build.gradle")

        for (filename in buildFiles) {
            val files = FilenameIndex.getFilesByName(
                project,
                filename,
                GlobalSearchScope.projectScope(project)
            )

            if (files.isNotEmpty()) {
                val buildFile = files.first().virtualFile
                return addToGradleFile(project, buildFile, pluginManager, plugin, filename.endsWith(".kts"))
            }
        }

        return false
    }

    /**
     * Adds plugin to a specific Gradle build file.
     */
    private fun addToGradleFile(
        project: Project,
        buildFile: VirtualFile,
        pluginManager: RoqPluginManager,
        plugin: RoqPluginManager.RoqPlugin,
        isKotlin: Boolean
    ): Boolean {
        val version = pluginManager.getRoqVersion() ?: RoqPluginManager.DEFAULT_ROQ_VERSION

        try {
            WriteCommandAction.runWriteCommandAction(project) {
                val content = String(buildFile.contentsToByteArray())

                val quote = if (isKotlin) "\"" else "'"
                val dependencyLine = "    implementation ${quote}${RoqPluginManager.ROQ_GROUP_ID}:${plugin.artifactId}:$version${quote}"

                val updatedContent = if (content.contains("dependencies {")) {
                    // Add to existing dependencies block
                    content.replace(
                        "dependencies {",
                        "dependencies {\n$dependencyLine"
                    )
                } else {
                    // Add dependencies block
                    content + "\n\ndependencies {\n$dependencyLine\n}\n"
                }

                buildFile.setBinaryContent(updatedContent.toByteArray())
                buildFile.refresh(false, false)
            }
            return true
        } catch (e: IOException) {
            thisLogger().error("Failed to update Gradle build file", e)
            return false
        }
    }
}
