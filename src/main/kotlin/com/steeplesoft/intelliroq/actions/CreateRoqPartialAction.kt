package com.steeplesoft.intelliroq.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteAction
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
 * Action to create a new partial in the templates/partials/ directory.
 */
class CreateRoqPartialAction : AnAction() {

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

        // Get or create templates/partials directory
        val partialsDir = getOrCreatePartialsDir(project) ?: run {
            Messages.showErrorDialog(
                project,
                "Failed to create templates/partials directory.",
                "Directory Error"
            )
            return
        }

        // Prompt for partial name
        val fileName = Messages.showInputDialog(
            project,
            "Enter the name for the partial (e.g., header.html, footer.html):",
            "Create Roq Partial",
            Messages.getQuestionIcon(),
            "",
            object : InputValidator {
                override fun checkInput(inputString: String): Boolean {
                    return inputString.isNotBlank() && inputString.endsWith(".html")
                }

                override fun canClose(inputString: String): Boolean = checkInput(inputString)
            }
        ) ?: return

        try {
            val newFile = createPartialFile(partialsDir, fileName)
            // Open the file in editor
            FileEditorManager.getInstance(project).openFile(newFile, true)
        } catch (ex: Exception) {
            thisLogger().error("Failed to create partial", ex)
            Messages.showErrorDialog(
                project,
                "Failed to create partial: ${ex.message}",
                "Creation Error"
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
        e.presentation.isVisible = detector.isRoqProject()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    private fun getOrCreatePartialsDir(project: Project): VirtualFile? {
        return WriteAction.computeAndWait<VirtualFile?, IOException> {
            val baseDir = project.baseDir ?: return@computeAndWait null

            var templatesDir = baseDir.findChild("templates")
            if (templatesDir == null) {
                templatesDir = baseDir.createChildDirectory(this, "templates")
            }

            var partialsDir = templatesDir.findChild("partials")
            if (partialsDir == null) {
                partialsDir = templatesDir.createChildDirectory(this, "partials")
            }

            partialsDir
        }
    }

    private fun createPartialFile(partialsDir: VirtualFile, fileName: String): VirtualFile {
        return WriteAction.computeAndWait<VirtualFile, IOException> {
            val file = partialsDir.createChildData(this, fileName)

            // Create template content with basic Qute partial structure
            val baseName = fileName.removeSuffix(".html")
            val content = """
                {! Partial: ${baseName} !}
                <div class="${baseName}">
                    {! Add your partial content here !}
                </div>
            """.trimIndent()

            file.setBinaryContent(content.toByteArray())
            file
        }
    }
}
