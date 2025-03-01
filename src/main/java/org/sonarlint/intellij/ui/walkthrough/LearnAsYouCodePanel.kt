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
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import java.awt.Font
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable

private const val NEXT_BUTTON_TEXT = "Next: Connect with Your Team"
private const val STEP = "Step 2/4"
private const val TITLE = "Learn as You Code"

class LearnAsYouCodePanel(project: Project) : AbstractWalkthroughPanel(
    SonarLintIcons.WALKTHROUGH_LEARN_AS_YOU_CODE,
    STEP,
    TITLE,
    PREVIOUS,
    NEXT_BUTTON_TEXT
) {

    init {
        mainScrollPane.viewport.view = createLearnAsYouCodePageText(labelFont, project)

        //The reason we add the center panel here and not in the abstract panel is we would be leaking this(panel) in the constructor
        //Which could cause issue in a multithreaded environment since it can be accessed before the constructor is finished
        addCenterPanel(
            pageStepLabel, pageTitleLabel, mainScrollPane,
            backButtonPanel, nextButtonPanel, this, pageImageLabel
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
