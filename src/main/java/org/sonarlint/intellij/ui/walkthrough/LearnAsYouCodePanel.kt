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
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.FONT
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.PREVIOUS
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.SONARQUBE_FOR_IDE
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.addCenterPanel

private const val CURRENT_FILE = "Current File"
private const val NEXT_BUTTON_TEXT = "Next: Connect with Your Team"

class LearnAsYouCodePanel(project: Project) :
    JBPanel<JBPanel<*>>(BorderLayout()) {

    val nextButton = JButton(NEXT_BUTTON_TEXT)
    val backButton = JButton(PREVIOUS)

    init {
        val font = UIUtil.getLabelFont()

        val learnAsYouCodeImageLabel =
            JBLabel(SonarLintIcons.WALKTHROUGH_LEARN_AS_YOU_CODE)

        val learnAsYouCodeStepLabel = JBLabel("Step 2/4", SwingConstants.LEFT)
        learnAsYouCodeStepLabel.font = Font(FONT, Font.PLAIN, 14)

        val learnAsYouCodePageLabel = JBLabel("Learn as You Code")
        learnAsYouCodePageLabel.font = Font(FONT, Font.BOLD, 16)
        val learnAsYouCodeText = createLearnAsYouCodePageText(font, project)

        val learnAsYouCodeScrollPane = JScrollPane(learnAsYouCodeText)
        learnAsYouCodeScrollPane.border = BorderFactory.createEmptyBorder()
        learnAsYouCodeScrollPane.preferredSize = Dimension(WIDTH, HEIGHT)

        val learnAsYouCodePageBackButtonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT))
        learnAsYouCodePageBackButtonPanel.add(backButton)

        val learnAsYouCodePageNextButtonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT))
        learnAsYouCodePageNextButtonPanel.add(nextButton)

        addCenterPanel(
            learnAsYouCodeStepLabel, learnAsYouCodePageLabel, learnAsYouCodeScrollPane,
            learnAsYouCodePageBackButtonPanel, learnAsYouCodePageNextButtonPanel, this, learnAsYouCodeImageLabel
        )
    }

    private fun createLearnAsYouCodePageText(font: Font, project: Project): JEditorPane {
        val descriptionPane = SwingHelper.createHtmlViewer(false, font, null, null)
        descriptionPane.apply {
            text = """
                Check the <a href="#currentFile">Current File</a> view: When $SONARQUBE_FOR_IDE found something, click on the issue to 
                get the rule description and an example of compliant code.<br><br>
                Some rules offer quick fixes when you hover over the issue location.<br><br>
                Finally you can disable rules in the <a href="#settings">settings</a>.
            """.trimIndent()
            border = JBUI.Borders.empty(8, 8)

            addHyperlinkListener(object : HyperlinkAdapter() {
                override fun hyperlinkActivated(e: HyperlinkEvent) {
                    if ("#currentFile" == e.description) {
                        val sonarqubeToolWindow = ToolWindowManager.getInstance(project).getToolWindow(SONARQUBE_FOR_IDE)
                        sonarqubeToolWindow?.let {
                            if (!sonarqubeToolWindow.isVisible) {
                                sonarqubeToolWindow.activate(null)
                            }
                            val currentFileContent = sonarqubeToolWindow.contentManager.findContent(CURRENT_FILE)
                            currentFileContent?.let {
                                sonarqubeToolWindow.contentManager.setSelectedContent(currentFileContent)
                            }
                        }

                    } else if ("#settings" == e.description) {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, SonarLintGlobalConfigurable::class.java)
                    }
                }
            })
        }

        return descriptionPane
    }
}
