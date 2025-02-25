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

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.telemetry.LinkTelemetry
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.FONT
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.PREVIOUS
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.SONARQUBE_FOR_IDE
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.addCenterPanel

private const val LOG = "Log"
private const val CLOSE = "Close"

class ReachOutToUsPanel(project: Project) :
    JBPanel<JBPanel<*>>(BorderLayout()) {

    val closeButton = JButton(CLOSE)
    val backButton = JButton(PREVIOUS)

    init {
        val font = UIUtil.getLabelFont()

        val icon = SonarLintIcons.WALKTHROUGH_REACH_OUT_TO_US
        val reachOutToUsImageLabel = JBLabel(icon)

        val reachOutToUsStepLabel = JBLabel("Step 4/4", SwingConstants.LEFT)
        reachOutToUsStepLabel.font = Font(FONT, Font.PLAIN, 14)

        val reachOutToUsLabel = JBLabel("Reach out to us")
        reachOutToUsLabel.font = Font(FONT, Font.BOLD, 16)

        val reachOutToUsDescription = createReachOutToUsPageText(font, project)

        val reachOutToUsPane = JScrollPane(reachOutToUsDescription)
        reachOutToUsPane.border = null
        reachOutToUsPane.preferredSize = Dimension(WIDTH, 100)

        val reachOutToUsBackButtonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT))
        reachOutToUsBackButtonPanel.add(backButton)

        val closeButtonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT))
        closeButtonPanel.add(closeButton)

        addCenterPanel(
            reachOutToUsStepLabel, reachOutToUsLabel, reachOutToUsPane, reachOutToUsBackButtonPanel,
            closeButtonPanel, this, reachOutToUsImageLabel
        )
    }

    private fun createReachOutToUsPageText(font: Font, project: Project): JEditorPane {
        val descriptionPane = SwingHelper.createHtmlViewer(false, font, null, null)
        descriptionPane.apply {
            text = """
                You suspect any issue with ${SONARQUBE_FOR_IDE}? Check the <a href="#logView">log view</a>.<br>
                Share the verbose logs with us via the <a href="#communityForum">Community forum</a> in case of problem. We will be happy to help 
                you debug.<br><br>
                Learn more about $SONARQUBE_FOR_IDE through <a href="#docs">our docs</a>.
            """.trimIndent()
            border = JBUI.Borders.empty(8, 8)

            addHyperlinkListener(object : HyperlinkAdapter() {
                override fun hyperlinkActivated(e: HyperlinkEvent) {
                    when (e.description) {
                        "#logView" -> {
                            val sonarqubeToolWindow = ToolWindowManager.getInstance(project).getToolWindow(SONARQUBE_FOR_IDE)
                            sonarqubeToolWindow?.let {
                                if (!sonarqubeToolWindow.isVisible) {
                                    sonarqubeToolWindow.activate(null)
                                }
                                val currentFileContent = sonarqubeToolWindow.contentManager.findContent(LOG)

                                currentFileContent?.let {
                                    sonarqubeToolWindow.contentManager.setSelectedContent(currentFileContent)
                                }
                            }
                        }

                        "#communityForum" -> LinkTelemetry.COMMUNITY_PAGE.browseWithTelemetry()
                        "#docs" -> LinkTelemetry.BASE_DOCS_PAGE.browseWithTelemetry()
                    }
                }
            })
        }

        return descriptionPane
    }
}
