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
import org.sonarlint.intellij.telemetry.LinkTelemetry

private const val CLOSE = "Close"

class ReachOutToUsPanel(project: Project) : AbstractWalkthroughPanel() {

    val closeButton = JButton(CLOSE)
    val backButton = JButton(PREVIOUS)

    init {
        val labelFont = UIUtil.getLabelFont()

        val icon = SonarLintIcons.WALKTHROUGH_REACH_OUT_TO_US
        val reachOutToUsImageLabel = JBLabel(icon)

        val reachOutToUsStepLabel = JBLabel("Step 4/4", SwingConstants.LEFT).apply { font = Font(FONT, Font.PLAIN, 14) }
        val reachOutToUsLabel = JBLabel("Reach out to us").apply { font = Font(FONT, Font.BOLD, 16)}

        val reachOutToUsDescription = createReachOutToUsPageText(labelFont, project)

        //The old UI is not looking good with JBScrollPane, so we are using JScrollPane
        val reachOutToUsPane = JScrollPane(reachOutToUsDescription)

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
                            SonarLintUtils.getService(project, SonarLintToolWindow::class.java).openLogTab()
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
