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
import com.steeplesoft.intelliroq.services.RoqProjectDetector
import java.io.IOException

/**
 * Action to initialize a Roq project structure in the current project.
 */
class InitializeRoqProjectAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val detector = project.service<RoqProjectDetector>()

        // Check if already a Roq project
        if (detector.isRoqProject()) {
            Messages.showInfoMessage(
                project,
                "This project already appears to be a Roq project.",
                "Roq Project Already Initialized"
            )
            return
        }

        // Confirm with user
        val result = Messages.showYesNoDialog(
            project,
            "This will create the Roq project structure (content/, templates/, data/, etc.) in your project root.\n\n" +
            "Do you want to continue?",
            "Initialize Roq Project",
            Messages.getQuestionIcon()
        )

        if (result != Messages.YES) {
            return
        }

        try {
            initializeRoqStructure(project)
            Messages.showInfoMessage(
                project,
                "Roq project structure has been created successfully!\n\n" +
                "Next steps:\n" +
                "1. Add Roq dependencies to your build file\n" +
                "2. Configure application.properties\n" +
                "3. Start creating content in the content/ directory",
                "Roq Project Initialized"
            )
        } catch (ex: Exception) {
            thisLogger().error("Failed to initialize Roq project", ex)
            Messages.showErrorDialog(
                project,
                "Failed to initialize Roq project: ${ex.message}",
                "Initialization Error"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    /**
     * Creates the Roq project structure and initial files.
     */
    private fun initializeRoqStructure(project: Project) {
        val baseDir = project.baseDir ?: throw IOException("Project base directory not found")

        WriteCommandAction.runWriteCommandAction(project) {
            // Create main directories
            val contentDir = baseDir.createChildDirectory(this, "content")
            val templatesDir = baseDir.createChildDirectory(this, "templates")
            val dataDir = baseDir.createChildDirectory(this, "data")
            baseDir.createChildDirectory(this, "static")
            baseDir.createChildDirectory(this, "public")

            // Create template subdirectories
            templatesDir.createChildDirectory(this, "layouts")
            templatesDir.createChildDirectory(this, "partials")

            // Create initial content file
            createIndexPage(contentDir)

            // Create sample data file
            createSampleData(dataDir)

            // Create or update application.properties
            createApplicationProperties(project, baseDir)

            // Refresh the file system
            baseDir.refresh(false, true)
        }
    }

    /**
     * Creates a sample index page.
     */
    private fun createIndexPage(contentDir: VirtualFile) {
        val indexFile = contentDir.createChildData(this, "index.md")
        val content = """
            ---
            layout: :theme/page
            title: Welcome to Roq
            ---

            # Welcome to Roq

            This is your Roq static site generator project!

            ## Getting Started

            1. Add Roq dependencies to your build file
            2. Run in dev mode: `quarkus dev`
            3. Start creating content!

            ## Project Structure

            - `content/` - Your pages and blog posts (Markdown, AsciiDoc, HTML)
            - `templates/` - Custom layouts and partials (Qute templates)
            - `data/` - Structured data files (YAML, JSON)
            - `static/` - Static assets (images, downloads)
            - `public/` - Public assets served at root

            Happy coding!
        """.trimIndent()

        indexFile.setBinaryContent(content.toByteArray())
    }

    /**
     * Creates a sample data file.
     */
    private fun createSampleData(dataDir: VirtualFile) {
        val menuFile = dataDir.createChildData(this, "menu.yml")
        val content = """
            # Main navigation menu
            items:
              - name: Home
                url: /
              - name: Blog
                url: /posts/
              - name: About
                url: /about/
        """.trimIndent()

        menuFile.setBinaryContent(content.toByteArray())
    }

    /**
     * Creates or updates application.properties with Roq configuration.
     */
    private fun createApplicationProperties(project: Project, baseDir: VirtualFile) {
        val resourcesDir = getOrCreateResourcesDir(baseDir)
        val propsFile = resourcesDir.findChild("application.properties")

        val roqConfig = """
            # Roq Configuration
            # See: https://docs.quarkiverse.io/quarkus-roq/dev/index.html

            # Site metadata
            site.url=http://localhost:8080
            site.title=My Roq Site
            site.description=A static site built with Roq

            # Collections
            site.collections."posts".enabled=true
            site.collections."posts".layout=:theme/post
            site.collections."posts".future=false

            # Generator configuration (for static build)
            quarkus.roq.generator.paths=/,/posts/**
            quarkus.roq.generator.output-dir=roq

        """.trimIndent()

        if (propsFile == null) {
            // Create new file
            val newFile = resourcesDir.createChildData(this, "application.properties")
            newFile.setBinaryContent(roqConfig.toByteArray())
        } else {
            // Append to existing file
            val existingContent = String(propsFile.contentsToByteArray())
            if (!existingContent.contains("quarkus.roq")) {
                val updatedContent = existingContent.trimEnd() + "\n\n" + roqConfig
                propsFile.setBinaryContent(updatedContent.toByteArray())
            }
        }
    }

    /**
     * Gets or creates src/main/resources directory.
     */
    private fun getOrCreateResourcesDir(baseDir: VirtualFile): VirtualFile {
        var srcDir = baseDir.findChild("src")
        if (srcDir == null) {
            srcDir = baseDir.createChildDirectory(this, "src")
        }

        var mainDir = srcDir.findChild("main")
        if (mainDir == null) {
            mainDir = srcDir.createChildDirectory(this, "main")
        }

        var resourcesDir = mainDir.findChild("resources")
        if (resourcesDir == null) {
            resourcesDir = mainDir.createChildDirectory(this, "resources")
        }

        return resourcesDir
    }
}
