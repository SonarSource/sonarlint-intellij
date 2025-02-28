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
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.RULE_SECTION_LINK
import org.sonarlint.intellij.telemetry.LinkTelemetry

private const val STEP_1_4 = "Step 1/4"

class WelcomePanel(project: Project) : AbstractWalkthroughPanel() {

    val nextButton = JButton("Next: Learn as You Code")

    init {
        val labelFont = UIUtil.getLabelFont()

        val icon = SonarLintIcons.WALKTHROUGH_WELCOME
        val welcomeImageLabel = JBLabel(icon)

        val welcomeStepLabel = JBLabel(STEP_1_4, SwingConstants.LEFT).apply { font = Font(FONT, Font.PLAIN, 14) }
        val titleLabel = JBLabel("Get started", SwingConstants.LEFT).apply { font = Font(FONT, Font.BOLD, 16) }

        val welcomePageText = createWelcomePageText(labelFont, project)

        //The old UI is not looking good with JBScrollPane, so we are using JScrollPane
        val welcomePageScrollPane = JScrollPane(welcomePageText)

        val welcomePageNextButtonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT))
        welcomePageNextButtonPanel.add(nextButton)

        createWelcomePageLayout(
            welcomeStepLabel, titleLabel, welcomePageScrollPane, welcomePageNextButtonPanel, this,
            welcomeImageLabel
        )
    }

    private fun createWelcomePageLayout(
        stepLabel: JBLabel, titleLabel: JBLabel, welcomePageScrollPane: JScrollPane,
        welcomePageNextButtonPanel: JBPanel<JBPanel<*>>, welcomePanel: JBPanel<JBPanel<*>>, welcomePageImageLabel: JBLabel,
    ) {
        val gbc = GridBagConstraints()

        val centerPanel = createCenterPanel(stepLabel, titleLabel, welcomePageScrollPane, gbc)

        gbc.anchor = GridBagConstraints.SOUTHEAST
        provideCommonButtonConstraints(gbc)
        centerPanel.add(welcomePageNextButtonPanel, gbc)

        welcomePanel.add(welcomePageImageLabel, BorderLayout.NORTH)
        welcomePanel.add(centerPanel, BorderLayout.CENTER)
    }

    private fun createWelcomePageText(font: Font, project: Project): JEditorPane {
        val descriptionPane = SwingHelper.createHtmlViewer(false, font, null, null)
        descriptionPane.apply {
            text = """
                ${SONARQUBE_FOR_IDE} supports the analysis of 15+ languages including
                Python, Java, Javascript, IaC domains along with secrets detection.
                <a href="${RULE_SECTION_LINK}">Learn more</a>.<br><br>
                Detect issues while you code in open files or run the analysis on more file in the <a href="#reportView">report view</a>.<br><br>
                Open a file and start your clean code journey.
            """.trimIndent()
            border = JBUI.Borders.empty(8, 8)

            addHyperlinkListener(object : HyperlinkAdapter() {
                override fun hyperlinkActivated(e: HyperlinkEvent) {
                    if ("#reportView" == e.description) {
                        SonarLintUtils.getService(project, SonarLintToolWindow::class.java).openReportTab()
                    } else {
                        LinkTelemetry.RULE_SELECTION_PAGE.browseWithTelemetry()
                    }
                }
            })
        }

        return descriptionPane
    }
}
