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
import org.sonarlint.intellij.config.project.SonarLintProjectConfigurable
import org.sonarlint.intellij.telemetry.LinkTelemetry

private const val NEXT_BUTTON_TEXT = "Next: Reach Out to Us"
private const val STEP = "Step 3/4"
private const val TITLE = "Connect with your team"

class ConnectWithYourTeamPanel(project: Project) : AbstractWalkthroughPanel(
    SonarLintIcons.WALKTHROUGH_CONNECT_WITH_YOUR_TEAM, STEP, TITLE, PREVIOUS, NEXT_BUTTON_TEXT
) {

    init {
        mainScrollPane.viewport.view = createConnectWithYourTeamPageText(labelFont, project)

        //The reason we add the center panel here and not in the abstract panel is we would be leaking this(panel) in the constructor
        //Which could cause issue in a multithreaded environment since it can be accessed before the constructor is finished
        addCenterPanel(
            pageStepLabel, pageTitleLabel, mainScrollPane,
            backButtonPanel, nextButtonPanel, this, pageImageLabel
        )
    }

    private fun createConnectWithYourTeamPageText(font: Font, project: Project): JEditorPane {
        val descriptionPane = SwingHelper.createHtmlViewer(false, font, null, null)
        descriptionPane.apply {
            text = """
                $SONARQUBE_FOR_IDE can connect to SonarQube (Server, Cloud) using connected mode;
                this allows your entire team to use the same rules and applied quality standards.<br><br>
                When using connected mode, you can highlight advanced issues like <a href="#taintVulnerabilities">taint vulnerabilities</a> 
                and analyze more languages that aren’t available with a local analysis. SonarQube (Server, Cloud) can also generate
                <a href="#aiFixSuggestions">AI fix suggestions</a>, and you can open them right in your IDE!<br><br>
                Are you already using SonarQube (Server, Cloud)? <a href="#setupConnection">Click here</a> to set up a connection!
            """.trimIndent()
            border = JBUI.Borders.empty(8)

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
