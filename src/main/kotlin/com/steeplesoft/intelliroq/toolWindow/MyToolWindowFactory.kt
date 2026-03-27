package com.steeplesoft.intelliroq.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.steeplesoft.intelliroq.services.RoqPluginManager
import com.steeplesoft.intelliroq.services.RoqProjectDetector
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val roqToolWindow = RoqToolWindow(project)
        val content = ContentFactory.getInstance().createContent(roqToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class RoqToolWindow(private val project: Project) {

        private val detector = project.service<RoqProjectDetector>()
        private val pluginManager = project.service<RoqPluginManager>()

        fun getContent(): JComponent {
            val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
            mainPanel.border = JBUI.Borders.empty(10)

            if (!detector.isRoqProject()) {
                mainPanel.add(createNotRoqProjectPanel(), BorderLayout.CENTER)
            } else {
                mainPanel.add(createRoqProjectPanel(), BorderLayout.CENTER)
            }

            return JBScrollPane(mainPanel)
        }

        private fun createNotRoqProjectPanel(): JPanel {
            val panel = JBPanel<JBPanel<*>>(BorderLayout())
            panel.border = JBUI.Borders.empty(20)

            val messageLabel = JBLabel("<html><div style='text-align: center;'>" +
                    "<h2>Not a Roq Project</h2>" +
                    "<p>This project doesn't appear to be a Roq static site project.</p>" +
                    "<p>Use <b>Tools > Roq > Initialize Roq Project Structure</b> to get started.</p>" +
                    "</div></html>")
            messageLabel.horizontalAlignment = SwingConstants.CENTER

            panel.add(messageLabel, BorderLayout.CENTER)
            return panel
        }

        private fun createRoqProjectPanel(): JPanel {
            val panel = JBPanel<JBPanel<*>>(GridBagLayout())
            val gbc = GridBagConstraints()
            gbc.gridx = 0
            gbc.gridy = 0
            gbc.weightx = 1.0
            gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.insets = JBUI.insets(5)

            // Project Overview Section
            panel.add(createSectionTitle("Project Overview"), gbc)
            gbc.gridy++
            panel.add(createProjectOverviewPanel(), gbc)

            gbc.gridy++
            panel.add(Box.createVerticalStrut(15), gbc)

            // Quick Actions Section
            gbc.gridy++
            panel.add(createSectionTitle("Quick Actions"), gbc)
            gbc.gridy++
            panel.add(createQuickActionsPanel(), gbc)

            gbc.gridy++
            panel.add(Box.createVerticalStrut(15), gbc)

            // Installed Plugins Section
            gbc.gridy++
            panel.add(createSectionTitle("Installed Plugins"), gbc)
            gbc.gridy++
            panel.add(createInstalledPluginsPanel(), gbc)

            // Filler
            gbc.gridy++
            gbc.weighty = 1.0
            gbc.fill = GridBagConstraints.BOTH
            panel.add(Box.createGlue(), gbc)

            return panel
        }

        private fun createSectionTitle(title: String): JComponent {
            val label = JBLabel("<html><b>$title</b></html>")
            label.border = JBUI.Borders.empty(5, 0)
            return label
        }

        private fun createProjectOverviewPanel(): JPanel {
            val panel = JBPanel<JBPanel<*>>(GridBagLayout())
            panel.border = JBUI.Borders.empty(5, 15)

            val gbc = GridBagConstraints()
            gbc.gridx = 0
            gbc.gridy = 0
            gbc.anchor = GridBagConstraints.WEST
            gbc.insets = JBUI.insets(2)

            val roqVersion = pluginManager.getRoqVersion() ?: "Not detected"
            val directories = detector.getRoqDirectories()

            panel.add(JBLabel("Roq Version: "), gbc)
            gbc.gridx = 1
            panel.add(JBLabel(roqVersion), gbc)

            gbc.gridx = 0
            gbc.gridy++
            panel.add(JBLabel("Content Directory: "), gbc)
            gbc.gridx = 1
            panel.add(JBLabel(if (directories.contentDir != null) "✓" else "✗"), gbc)

            gbc.gridx = 0
            gbc.gridy++
            panel.add(JBLabel("Templates Directory: "), gbc)
            gbc.gridx = 1
            panel.add(JBLabel(if (directories.templatesDir != null) "✓" else "✗"), gbc)

            gbc.gridx = 0
            gbc.gridy++
            panel.add(JBLabel("Data Directory: "), gbc)
            gbc.gridx = 1
            panel.add(JBLabel(if (directories.dataDir != null) "✓" else "✗"), gbc)

            return panel
        }

        private fun createQuickActionsPanel(): JPanel {
            val panel = JBPanel<JBPanel<*>>()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.border = JBUI.Borders.empty(5, 15)

            panel.add(createActionButton("New Data File", "Create a new YAML/JSON data file"))
            panel.add(Box.createVerticalStrut(5))
            panel.add(createActionButton("New Layout", "Create a new page layout template"))
            panel.add(Box.createVerticalStrut(5))
            panel.add(createActionButton("New Partial", "Create a new partial template"))
            panel.add(Box.createVerticalStrut(10))
            panel.add(createActionButton("Build Static Site", "Build the site for production"))

            return panel
        }

        private fun createInstalledPluginsPanel(): JPanel {
            val panel = JBPanel<JBPanel<*>>(BorderLayout())
            panel.border = JBUI.Borders.empty(5, 15)

            val installedPlugins = pluginManager.getInstalledPlugins()

            if (installedPlugins.isEmpty()) {
                val label = JBLabel("No plugins installed")
                label.foreground = JBUI.CurrentTheme.Label.disabledForeground()
                panel.add(label, BorderLayout.NORTH)
            } else {
                val pluginsPanel = JBPanel<JBPanel<*>>()
                pluginsPanel.layout = BoxLayout(pluginsPanel, BoxLayout.Y_AXIS)

                installedPlugins.forEach { installedPlugin ->
                    val pluginLabel = JBLabel("• ${installedPlugin.plugin.displayName}" +
                            (installedPlugin.version?.let { " ($it)" } ?: ""))
                    pluginsPanel.add(pluginLabel)
                    pluginsPanel.add(Box.createVerticalStrut(3))
                }

                panel.add(pluginsPanel, BorderLayout.NORTH)
            }

            val addPluginButton = JButton("Add Plugin...")
            addPluginButton.addActionListener {
                // This would trigger the add plugin action
                Messages.showMessageDialog(
                    project,
                    "Use Tools > Roq > Add Roq Plugin... to add plugins",
                    "Add Plugin",
                    Messages.getInformationIcon()
                )
            }
            panel.add(addPluginButton, BorderLayout.SOUTH)

            return panel
        }

        private fun createActionButton(text: String, tooltip: String): JButton {
            val button = JButton(text)
            button.toolTipText = tooltip
            button.alignmentX = JComponent.LEFT_ALIGNMENT
            button.addActionListener {
                Messages.showMessageDialog(
                    project,
                    "Use Tools > Roq menu for this action",
                    text,
                    Messages.getInformationIcon()
                )
            }
            return button
        }
    }
}
