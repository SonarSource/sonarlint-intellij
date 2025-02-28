/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.ui.walkthrough

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.project.SonarLintProjectConfigurable
import org.sonarlint.intellij.telemetry.LinkTelemetry

private const val NEXT_BUTTON_TEXT = "Next: Reach Out to Us"

class ConnectWithYourTeamPanel(project: Project) : AbstractWalkthroughPanel() {

    val backButton = JButton(PREVIOUS)
    val nextButton = JButton(NEXT_BUTTON_TEXT)

    init {
        val labelFont = UIUtil.getLabelFont()

        val connectWithYourTeamImageLabel = JBLabel(SonarLintIcons.WALKTHROUGH_CONNECT_WITH_YOUR_TEAM)

        val connectWithYourTeamStepLabel = JBLabel("Step 3/4", SwingConstants.LEFT).apply { font = Font(FONT, Font.PLAIN, 14) }
        val connectWithYourTeamLabel = JBLabel("Connect with your team").apply { font = Font(FONT, Font.BOLD, 16) }

        val connectWithYourTeamText = createConnectWithYourTeamPageText(labelFont, project)

        //The old UI is not looking good with JBScrollPane, so we are using JScrollPane
        val connectWithYourTeamScrollPane = JScrollPane(connectWithYourTeamText)

        val connectWithYourTeamBackButtonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT))
        connectWithYourTeamBackButtonPanel.add(backButton)

        val connectWithYourTeamNextButtonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT))
        connectWithYourTeamNextButtonPanel.add(nextButton)

        addCenterPanel(
            connectWithYourTeamStepLabel, connectWithYourTeamLabel, connectWithYourTeamScrollPane,
            connectWithYourTeamBackButtonPanel, connectWithYourTeamNextButtonPanel, this, connectWithYourTeamImageLabel
        )
    }

    private fun createConnectWithYourTeamPageText(font: Font, project: Project): JEditorPane {
        val descriptionPane = SwingHelper.createHtmlViewer(false, font, null, null)
        descriptionPane.apply {
            text = """
                Apply the same set of rules as your team by using $SONARQUBE_FOR_IDE in Connected Mode with SonarQube Cloud or SonarQube Server.<br><br>
                With Connected Mode, benefit from advanced analysis like <a href="#taintVulnerabilities">Taint Vulnerabilities</a> and open
                issues and <a href="#aiFixSuggestions">AI fix suggestions</a> from SonarQube (Server, Cloud) in the IDE.<br><br>
                Already using SonarQube (Server, Cloud)? <a href="#setupConnection">Set up a connection</a>
            """.trimIndent()
            border = JBUI.Borders.empty(8, 8)

            addHyperlinkListener(object : HyperlinkAdapter() {
                override fun hyperlinkActivated(e: HyperlinkEvent) {
                    when (e.description) {
                        "#setupConnection" -> {
                            val configurable = SonarLintProjectConfigurable(project)
                            ShowSettingsUtil.getInstance().editConfigurable(project, configurable)
                        }

                        "#taintVulnerabilities" -> {
                            SonarLintUtils.getService(project, SonarLintToolWindow::class.java).openTaintVulnerabilityTab()
                        }

                        else -> {
                            LinkTelemetry.AI_FIX_SUGGESTIONS_PAGE.browseWithTelemetry()
                        }
                    }
                }
            })
        }

        return descriptionPane
    }
}
