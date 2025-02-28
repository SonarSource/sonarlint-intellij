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
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable

private const val NEXT_BUTTON_TEXT = "Next: Connect with Your Team"

class LearnAsYouCodePanel(project: Project) : AbstractWalkthroughPanel() {

    val nextButton = JButton(NEXT_BUTTON_TEXT)
    val backButton = JButton(PREVIOUS)

    init {
        val labelFont = UIUtil.getLabelFont()

        val learnAsYouCodeImageLabel =
            JBLabel(SonarLintIcons.WALKTHROUGH_LEARN_AS_YOU_CODE)

        val learnAsYouCodeStepLabel = JBLabel("Step 2/4", SwingConstants.LEFT).apply { font = Font(FONT, Font.PLAIN, 14) }

        val learnAsYouCodePageLabel = JBLabel("Learn as You Code").apply { font = Font(FONT, Font.BOLD, 16) }
        val learnAsYouCodeText = createLearnAsYouCodePageText(labelFont, project)

        //The old UI is not looking good with JBScrollPane, so we are using JScrollPane
        val learnAsYouCodeScrollPane = JScrollPane(learnAsYouCodeText)

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
                Take a look at the <a href="#currentFile">Current File</a> tab: When $SONARQUBE_FOR_IDE finds an issue, select it to 
                get its rule description and an example of compliant code.<br><br>
                Double-click an issue in the Report tab to open its location in the Editor.
                Some rules offer quick fixes when you hover over the issue location.<br><br>
                If needed, you can disable rules in the <a href="#settings">Settings</a>.
            """.trimIndent()
            border = JBUI.Borders.empty(8, 8)

            addHyperlinkListener(object : HyperlinkAdapter() {
                override fun hyperlinkActivated(e: HyperlinkEvent) {
                    when (e.description) {
                        "#currentFile" -> {
                            SonarLintUtils.getService(project, SonarLintToolWindow::class.java).openCurrentFileTab()
                        }

                        "#settings" -> {
                            ShowSettingsUtil.getInstance().showSettingsDialog(project, SonarLintGlobalConfigurable::class.java)
                        }
                    }
                }
            })
        }

        return descriptionPane
    }
}
