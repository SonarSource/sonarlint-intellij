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
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import java.awt.Font
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.RULE_SECTION_LINK
import org.sonarlint.intellij.telemetry.LinkTelemetry

private const val STEP = "Step 1/4"
private const val TITLE = "Get started"

private const val NEXT_BUTTON_TEXT = "Next: Learn as You Code"

class WelcomePanel(project: Project) : AbstractWalkthroughPanel(SonarLintIcons.WALKTHROUGH_WELCOME, STEP, TITLE, null, NEXT_BUTTON_TEXT) {

    init {
        mainScrollPane.viewport.view = createWelcomePageText(labelFont, project)

        //The reason we add the center panel here and not in the abstract panel is we would be leaking this(panel) in the constructor
        //Which could cause issue in a multithreaded environment since it can be accessed before the constructor is finished
        addCenterPanel(
            pageStepLabel, pageTitleLabel, mainScrollPane,
            backButtonPanel, nextButtonPanel, this, pageImageLabel
        )
    }

    private fun createWelcomePageText(font: Font, project: Project): JEditorPane {
        val descriptionPane = SwingHelper.createHtmlViewer(false, font, null, null)
        descriptionPane.apply {
            text = """
                ${SONARQUBE_FOR_IDE} supports the analysis of more than 16 languages including
                Python, Java, Javascript, IaC domains, and secrets detection.
                <a href="${RULE_SECTION_LINK}">Learn more</a>.<br><br>
                Detect issues in your open file while you code or run an analysis on multiple files from the <a href="#reportView">Report tab</a>.<br><br>
                Open a file to start your Clean Code journey.
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
