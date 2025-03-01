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
import org.sonarlint.intellij.telemetry.LinkTelemetry

private const val CLOSE = "Close"
private const val STEP = "Step 4/4"
private const val TITLE = "Reach out to us"

class ReachOutToUsPanel(project: Project) : AbstractWalkthroughPanel(
    SonarLintIcons.WALKTHROUGH_REACH_OUT_TO_US,
    STEP,
    TITLE,
    PREVIOUS,
    CLOSE
) {
    init {
        mainScrollPane.viewport.view = createReachOutToUsPageText(labelFont, project)

        //The reason we add the center panel here and not in the abstract panel is we would be leaking this(panel) in the constructor
        //Which could cause issue in a multithreaded environment since it can be accessed before the constructor is finished
        addCenterPanel(
            pageStepLabel, pageTitleLabel, mainScrollPane,
            backButtonPanel, nextButtonPanel, this, pageImageLabel
        )
    }

    private fun createReachOutToUsPageText(font: Font, project: Project): JEditorPane {
        val descriptionPane = SwingHelper.createHtmlViewer(false, font, null, null)
        descriptionPane.apply {
            text = """
                Are you having problems with ${SONARQUBE_FOR_IDE}? Open the <a href="#logView">log tab</a> 
                and enable the Analysis logs and Verbose output.<br>
                Share your verbose logs with us in a post on the <a href="#communityForum">Community forum</a>.
                We are happy to help you debug!<br><br>
                Learn more about $SONARQUBE_FOR_IDE in the product <a href="#docs">documentation</a>.
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
