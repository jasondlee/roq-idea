package com.steeplesoft.intelliroq.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.steeplesoft.intelliroq.services.RoqProjectDetector
import java.io.IOException

/**
 * Action to create a new data file in the data/ directory.
 */
class CreateRoqDataFileAction : AnAction() {

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

        // Get data directory
        val roqDirs = detector.getRoqDirectories()
        val dataDir = roqDirs.dataDir

        if (dataDir == null || !dataDir.exists()) {
            Messages.showErrorDialog(
                project,
                "The data/ directory does not exist. Please create it first.",
                "Data Directory Missing"
            )
            return
        }

        // Prompt for file name
        val fileName = Messages.showInputDialog(
            project,
            "Enter the name for the data file (e.g., menu.yml, authors.json):",
            "Create Roq Data File",
            Messages.getQuestionIcon(),
            "",
            object : InputValidator {
                override fun checkInput(inputString: String): Boolean {
                    return inputString.isNotBlank() &&
                           (inputString.endsWith(".yml") ||
                            inputString.endsWith(".yaml") ||
                            inputString.endsWith(".json"))
                }

                override fun canClose(inputString: String): Boolean = checkInput(inputString)
            }
        ) ?: return

        try {
            val newFile = createDataFile(project, dataDir, fileName)
            // Open the file in editor
            FileEditorManager.getInstance(project).openFile(newFile, true)
        } catch (ex: Exception) {
            thisLogger().error("Failed to create data file", ex)
            Messages.showErrorDialog(
                project,
                "Failed to create data file: ${ex.message}",
                "Creation Error"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isVisible = false
            return
        }

        val detector = project.service<RoqProjectDetector>()
        e.presentation.isVisible = detector.isRoqProject()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    private fun createDataFile(project: Project, dataDir: VirtualFile, fileName: String): VirtualFile {
        return WriteCommandAction.writeCommandAction(project).compute<VirtualFile, IOException> {
            val file = dataDir.createChildData(this, fileName)

            // Create template content based on file type
            val content = when {
                fileName.endsWith(".yml") || fileName.endsWith(".yaml") -> """
                    # ${fileName}
                    # Add your YAML data here

                """.trimIndent()
                fileName.endsWith(".json") -> """
                    {
                      "description": "${fileName}"
                    }
                """.trimIndent()
                else -> ""
            }

            file.setBinaryContent(content.toByteArray())
            file
        }
    }
}
