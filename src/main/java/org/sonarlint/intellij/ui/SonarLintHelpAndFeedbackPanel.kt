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
package org.sonarlint.intellij.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.telemetry.LinkTelemetry

data class HelpCard(
    val title: String,
    val linkText: String,
    val link: LinkTelemetry
)

private val DOCUMENTATION_CARD = HelpCard(
    "Want to know more about our product?",
    "Read the Documentation",
    LinkTelemetry.BASE_DOCS_HELP
)

private val COMMUNITY_CARD = HelpCard(
    "SonarQube for IDE support",
    "Get help in the Community Forum",
    LinkTelemetry.COMMUNITY_HELP
)

private val FEATURE_CARD = HelpCard(
    "Are you missing any feature?",
    "Go to Suggested Features",
    LinkTelemetry.SUGGEST_FEATURE_HELP
)

class SonarLintHelpAndFeedbackPanel(private val project: Project) : SimpleToolWindowPanel(false, false) {

    private val topLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val cardPanel = JBPanel<SonarLintHelpAndFeedbackPanel>()

    init {
        layout = GridBagLayout()
        val constraints = GridBagConstraints().apply {
            gridx = 0
            gridy = GridBagConstraints.RELATIVE
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(10)
        }

        topLabel.apply {
            text = """
            Having issues with SonarQube for IDE? Open the <a href="#LogTab">Log tab</a>, 
            then <a href="#Verbose">enable the Analysis logs and Verbose output</a>.<br>
            Share your verbose logs with us in a post on the Community Forum. We are happy to help you debug!
        """.trimIndent()

            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    when (e.description) {
                        "#LogTab" -> getService(project, SonarLintToolWindow::class.java).openLogTab()
                        "#Verbose" -> LinkTelemetry.TROUBLESHOOTING_PAGE.browseWithTelemetry()
                    }
                }
            }
        }

        cardPanel.apply {
            layout = BoxLayout(cardPanel, BoxLayout.X_AXIS)
            add(generateHelpCard(DOCUMENTATION_CARD))
            add(Box.createHorizontalStrut(30))
            add(generateHelpCard(COMMUNITY_CARD))
            add(Box.createHorizontalStrut(30))
            add(generateHelpCard(FEATURE_CARD))
        }

        add(topLabel, constraints)

        constraints.gridy = 1
        add(JSeparator(SwingConstants.HORIZONTAL), constraints)

        constraints.gridy = 2
        constraints.fill = GridBagConstraints.BOTH
        constraints.weighty = 1.0
        add(cardPanel, constraints)
    }

    private fun generateHelpCard(card: HelpCard): JBPanel<SonarLintHelpAndFeedbackPanel> {
        return JBPanel<SonarLintHelpAndFeedbackPanel>(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 10, true, false)).apply {
            add(JBLabel(card.title).apply {
                font = JBFont.label().asBold()
            })
            add(ActionLink(card.linkText) { card.link.browseWithTelemetry() }.apply {
                setExternalLinkIcon()
            })
            maximumSize = Dimension(300, maximumSize.height)
        }
    }

}
