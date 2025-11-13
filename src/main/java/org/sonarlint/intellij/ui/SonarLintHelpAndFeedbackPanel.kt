/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.ui

import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.telemetry.LinkTelemetry
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.runOnPooledThread

data class HelpCard(
    val title: String,
    val linkText: String,
    val link: LinkTelemetry
)

private val DOCUMENTATION_CARD = HelpCard(
    "Want to know more about our product?",
    "Read the Documentation",
    LinkTelemetry.BASE_DOCS_HELP
)

private val COMMUNITY_CARD = HelpCard(
    "SonarQube for IDE support",
    "Get help in the Community Forum",
    LinkTelemetry.COMMUNITY_HELP
)

private val FEATURE_CARD = HelpCard(
    "Are you missing any feature?",
    "Go to Suggested Features",
    LinkTelemetry.SUGGEST_FEATURE_HELP
)

class SonarLintHelpAndFeedbackPanel(private val project: Project) : SimpleToolWindowPanel(false, false), Disposable {

    private val topLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val cardPanel = JBPanel<SonarLintHelpAndFeedbackPanel>()
    private val flightRecorderPanel = JBPanel<SonarLintHelpAndFeedbackPanel>()

    private lateinit var startButton: JButton
    private lateinit var stopButton: JButton
    private lateinit var threadDumpButton: JButton

    init {
        setupContent()
        setupConfigurationListener()
    }

    private fun setupContent() {
        val contentPanel = JBPanel<SonarLintHelpAndFeedbackPanel>()
        contentPanel.layout = GridBagLayout()
        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = GridBagConstraints.RELATIVE
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(5, 30)
        }

        topLabel.apply {
            text = """
            Having issues with SonarQube for IDE? Open the <a href="#LogTab">Log tab</a>, 
            then <a href="#Verbose">enable the Verbose output</a>.<br>
            Share your verbose logs with us in a post on the Community Forum. We are happy to help you debug!
        """.trimIndent()

            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    when (e.description) {
                        "#LogTab" -> getService(project, SonarLintToolWindow::class.java).openLogTab()
                        "#Verbose" -> LinkTelemetry.TROUBLESHOOTING_PAGE.browseWithTelemetry()
                    }
                }
            }
            border = JBUI.Borders.emptyTop(25)
        }

        cardPanel.apply {
            layout = BoxLayout(cardPanel, BoxLayout.X_AXIS)
            add(generateHelpCard(DOCUMENTATION_CARD))
            add(Box.createHorizontalStrut(30))
            add(generateHelpCard(COMMUNITY_CARD))
            add(Box.createHorizontalStrut(30))
            add(generateHelpCard(FEATURE_CARD))
        }

        contentPanel.add(topLabel, constraints)

        constraints.gridy = 1
        contentPanel.add(JSeparator(SwingConstants.HORIZONTAL), constraints)

        constraints.gridy = 2
        constraints.fill = GridBagConstraints.HORIZONTAL
        constraints.weighty = 0.0
        contentPanel.add(cardPanel, constraints)

        setupFlightRecorderPanel()

        constraints.gridy = 3
        contentPanel.add(JSeparator(SwingConstants.HORIZONTAL), constraints)

        constraints.gridy = 4
        constraints.fill = GridBagConstraints.BOTH
        constraints.weighty = 1.0
        contentPanel.add(flightRecorderPanel, constraints)

        val scrollPane = JBScrollPane(contentPanel).apply {
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
        }

        setContent(scrollPane)
    }

    private fun setupConfigurationListener() {
        val busConnection = project.messageBus.connect(this)
        busConnection.subscribe(GlobalConfigurationListener.TOPIC, object : GlobalConfigurationListener.Adapter() {
            override fun applied(previousSettings: SonarLintGlobalSettings, newSettings: SonarLintGlobalSettings) {
                if (previousSettings.isFlightRecorderEnabled != newSettings.isFlightRecorderEnabled) {
                    runOnUiThread(project) {
                        updateButtonStates()
                    }
                }
            }
        })
    }

    override fun dispose() {
        // Cleanup is handled automatically by the message bus connection
    }

    private fun generateHelpCard(card: HelpCard): JBPanel<SonarLintHelpAndFeedbackPanel> {
        return JBPanel<SonarLintHelpAndFeedbackPanel>(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, false)).apply {
            add(JBLabel(card.title).apply {
                font = JBFont.label().asBold()
            })
            add(ActionLink(card.linkText) { card.link.browseWithTelemetry() }.apply {
                setExternalLinkIcon()
            })
            maximumSize = Dimension(300, maximumSize.height)
        }
    }

    private fun setupFlightRecorderPanel() {
        flightRecorderPanel.apply {
            layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, false)

            add(JBLabel("Flight Recorder").apply {
                font = JBFont.label().asBold()
                border = JBUI.Borders.emptyLeft(3)
            })

            add(SwingHelper.createHtmlViewer(false, null, null, null).apply {
                text = """
                Flight Recorder mode enables advanced diagnostics for troubleshooting SonarQube for IDE issues. When enabled, it collects detailed execution traces, logs, and system metrics.<br>
                To report a problem, please share the generated session UUID (shown in the first notification) on our Community Forum.
            """.trimIndent()
            })

            val backendService = getService(BackendService::class.java)
            val buttonPanel = JBPanel<SonarLintHelpAndFeedbackPanel>()
            buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)

            startButton = JButton("Start Flight Recorder").apply {
                addActionListener { 
                    text = "Restarting..."
                    isEnabled = false
                    runOnPooledThread {
                        backendService.updateFlightRecorder(true)
                        runOnUiThread(project) {
                            text = "Start Flight Recorder"
                            updateButtonStates()
                        }
                    }
                }
            }

            stopButton = JButton("Stop Flight Recorder").apply {
                addActionListener { 
                    text = "Restarting..."
                    isEnabled = false
                    runOnPooledThread {
                        backendService.updateFlightRecorder(false)
                        runOnUiThread(project) {
                            text = "Stop Flight Recorder"
                            updateButtonStates()
                        }
                    }
                }
            }

            threadDumpButton = JButton("Generate Thread Dump").apply {
                addActionListener {
                    backendService.captureThreadDump()
                    SonarLintProjectNotifications.projectLessNotification(
                        null,
                        "Thread dump captured successfully.",
                        NotificationType.INFORMATION
                    )
                }
            }

            buttonPanel.add(startButton)
            buttonPanel.add(Box.createHorizontalStrut(10))
            buttonPanel.add(stopButton)
            buttonPanel.add(Box.createHorizontalStrut(10))
            buttonPanel.add(threadDumpButton)

            add(buttonPanel)

            updateButtonStates()
        }
    }

    fun updateButtonStates() {
        val isEnabled = getGlobalSettings().isFlightRecorderEnabled

        startButton.isEnabled = !isEnabled
        stopButton.isEnabled = isEnabled
        threadDumpButton.isEnabled = isEnabled
    }

}
