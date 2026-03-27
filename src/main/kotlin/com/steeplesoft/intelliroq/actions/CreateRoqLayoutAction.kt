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
 * Action to create a new layout in the templates/layouts/ directory.
 */
class CreateRoqLayoutAction : AnAction() {

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

        // Get or create templates/layouts directory
        val layoutsDir = getOrCreateLayoutsDir(project) ?: run {
            Messages.showErrorDialog(
                project,
                "Failed to create templates/layouts directory.",
                "Directory Error"
            )
            return
        }

        // Prompt for layout name
        val fileName = Messages.showInputDialog(
            project,
            "Enter the name for the layout (e.g., page.html, post.html):",
            "Create Roq Layout",
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
            val newFile = createLayoutFile(project, layoutsDir, fileName)
            // Open the file in editor
            FileEditorManager.getInstance(project).openFile(newFile, true)
        } catch (ex: Exception) {
            thisLogger().error("Failed to create layout", ex)
            Messages.showErrorDialog(
                project,
                "Failed to create layout: ${ex.message}",
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

    private fun getOrCreateLayoutsDir(project: Project): VirtualFile? {
        return WriteCommandAction.writeCommandAction(project).compute<VirtualFile?, IOException> {
            val baseDir = project.baseDir ?: return@compute null

            var templatesDir = baseDir.findChild("templates")
            if (templatesDir == null) {
                templatesDir = baseDir.createChildDirectory(this, "templates")
            }

            var layoutsDir = templatesDir.findChild("layouts")
            if (layoutsDir == null) {
                layoutsDir = templatesDir.createChildDirectory(this, "layouts")
            }

            layoutsDir
        }
    }

    private fun createLayoutFile(project: Project, layoutsDir: VirtualFile, fileName: String): VirtualFile {
        return WriteCommandAction.writeCommandAction(project).compute<VirtualFile, IOException> {
            val file = layoutsDir.createChildData(this, fileName)

            // Create template content with basic Qute structure
            val baseName = fileName.removeSuffix(".html")
            val content = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>{page.title ?: site.title}</title>
                    {#seo page site /}
                </head>
                <body>
                    <main>
                        {#insert}
                        <p>Default content for ${baseName} layout</p>
                        {/insert}
                    </main>
                </body>
                </html>
            """.trimIndent()

            file.setBinaryContent(content.toByteArray())
            file
        }
    }
}
