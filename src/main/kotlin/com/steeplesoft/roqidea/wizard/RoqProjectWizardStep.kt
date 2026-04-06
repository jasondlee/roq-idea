package com.steeplesoft.roqidea.wizard

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.steeplesoft.roqidea.services.RoqPluginManager
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.*

/**
 * Wizard step for configuring a new Roq project.
 */
class RoqProjectWizardStep(private val builder: RoqModuleBuilder) : ModuleWizardStep() {

    // Generation mode radio buttons
    private val codestartRadio = JRadioButton("Quarkus Codestart API", false)
    private val createProjectRadio = JRadioButton("CreateProject Command API (recommended)", true)

    // Build system radio buttons
    private val mavenRadio = JRadioButton("Maven", true)
    private val gradleRadio = JRadioButton("Gradle", false)

    private val siteUrlField = JBTextField("http://localhost:8080", 30)
    private val pluginCheckboxes = mutableMapOf<String, JCheckBox>()

    private val mainPanel: JPanel

    init {
        // Group generation mode radio buttons
        val generationModeGroup = ButtonGroup()
        generationModeGroup.add(codestartRadio)
        generationModeGroup.add(createProjectRadio)

        // Create generation mode panel
        val generationModePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(createProjectRadio)
            add(codestartRadio)
        }

        // Group build system radio buttons
        val buildSystemGroup = ButtonGroup()
        buildSystemGroup.add(mavenRadio)
        buildSystemGroup.add(gradleRadio)

        // Create build system panel
        val buildSystemPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(mavenRadio)
            add(Box.createHorizontalStrut(20))
            add(gradleRadio)
        }

        // Create plugins panel with 2 columns
        val pluginsPanel = createPluginsPanel()

        // Build main panel using FormBuilder
        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Project generation:"), generationModePanel)
            .addVerticalGap(10)
            .addSeparator()
            .addVerticalGap(10)
            .addLabeledComponent(JBLabel("Build system:"), buildSystemPanel)
            .addVerticalGap(10)
            .addLabeledComponent(JBLabel("Site URL:"), siteUrlField)
            .addVerticalGap(15)
            .addLabeledComponent(JBLabel("Roq plugins:"), JBLabel("Select the plugins to include in your project"))
            .addVerticalGap(5)
            .addComponentFillVertically(pluginsPanel, 0)
            .panel.apply {
                border = JBUI.Borders.empty(10)
            }
    }

    /**
     * Creates the plugins selection panel with checkboxes in 2 columns.
     */
    private fun createPluginsPanel(): JPanel {

        // Get plugins in a sorted order for consistent layout
        val plugins = RoqPluginManager.KNOWN_PLUGINS.values.sortedBy { plugin ->
            // Sort by name to get consistent ordering
            plugin.name
        }

        val pluginsPanel = JPanel(GridLayout(plugins.size, 1, 5, 5))
        // Create checkboxes for each plugin
        plugins.forEach { plugin ->
            val checkbox = JCheckBox("${plugin.displayName} - ${plugin.description}")

            // Pre-select and disable markdown (core plugin)
            if (plugin.isCore) {
                checkbox.isSelected = true
                checkbox.isEnabled = false
            }

            pluginCheckboxes[plugin.name] = checkbox
            pluginsPanel.add(checkbox)
        }

        return JPanel(BorderLayout()).apply {
            add(pluginsPanel, BorderLayout.NORTH)
        }
    }

    override fun getComponent(): JComponent = mainPanel

    override fun updateDataModel() {
        // Save generation mode preference
        builder.generationMode = when {
            codestartRadio.isSelected -> RoqModuleBuilder.GenerationMode.CODESTART_API
            createProjectRadio.isSelected -> RoqModuleBuilder.GenerationMode.CREATE_PROJECT_COMMAND
            else -> RoqModuleBuilder.GenerationMode.CREATE_PROJECT_COMMAND // default
        }

        // Save build system selection
        builder.buildSystem = if (mavenRadio.isSelected) {
            RoqModuleBuilder.BuildSystem.MAVEN
        } else {
            RoqModuleBuilder.BuildSystem.GRADLE
        }

        // Save site URL
        builder.siteUrl = siteUrlField.text.trim()

        // Save selected plugins
        builder.selectedPlugins.clear()
        pluginCheckboxes.forEach { (pluginName, checkbox) ->
            if (checkbox.isSelected) {
                builder.selectedPlugins.add(pluginName)
            }
        }
    }

    override fun validate(): Boolean {
        val url = siteUrlField.text.trim()

        // Check URL is not empty
        if (url.isEmpty()) {
            Messages.showErrorDialog(
                mainPanel,
                "Please enter a site URL.",
                "Site URL Required"
            )
            return false
        }

        // Check URL starts with http:// or https://
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Messages.showErrorDialog(
                mainPanel,
                "Site URL must start with 'http://' or 'https://'.",
                "Invalid Site URL"
            )
            return false
        }

        return true
    }
}
