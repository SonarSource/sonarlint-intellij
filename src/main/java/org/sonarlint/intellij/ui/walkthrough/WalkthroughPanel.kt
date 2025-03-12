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
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable
import org.sonarlint.intellij.config.project.SonarLintProjectConfigurable
import org.sonarlint.intellij.telemetry.LinkTelemetry

enum class WalkthroughActions(val id: String, val action: (Project) -> (Unit)) {
    REPORT_VIEW("#reportView", { project -> getService(project, SonarLintToolWindow::class.java).openReportTab() }),
    CONNECTED_MODE_LINK("#connectedModeLink", { LinkTelemetry.CONNECTED_MODE_DOCS.browseWithTelemetry() }),
    RULE_LINK("#ruleLink", { LinkTelemetry.USING_RULES_PAGE.browseWithTelemetry() }),
    INVESTIGATING_ISSUES_LINK("#investigatingIssuesLink", { LinkTelemetry.INVESTIGATING_ISSUES_PAGE.browseWithTelemetry() }),
    OPEN_IN_IDE_LINK("#openInIdeLink", { LinkTelemetry.OPEN_IN_IDE_PAGE.browseWithTelemetry() }),
    TROUBLESHOOTING_LINK("#troubleshootingLink", { LinkTelemetry.TROUBLESHOOTING_PAGE.browseWithTelemetry() }),
    CURRENT_FILE("#currentFile", { project -> getService(project, SonarLintToolWindow::class.java).openCurrentFileTab() }),
    SETTINGS("#settings", { project -> ShowSettingsUtil.getInstance().showSettingsDialog(project, SonarLintGlobalConfigurable::class.java) }),
    TAINT_VULNERABILITIES("#taintVulnerabilities", { project -> getService(project, SonarLintToolWindow::class.java).openTaintVulnerabilityTab() }),
    AI_FIX_SUGGESTIONS("#aiFixSuggestions", { LinkTelemetry.AI_FIX_SUGGESTIONS_PAGE.browseWithTelemetry() }),
    SETUP_CONNECTION("#setupConnection", { project -> ShowSettingsUtil.getInstance().editConfigurable(project, SonarLintProjectConfigurable(project)) }),
    LOG_VIEW("#logView", { project -> getService(project, SonarLintToolWindow::class.java).openLogTab() }),
    COMMUNITY_FORUM("#communityForum", { LinkTelemetry.COMMUNITY_PAGE.browseWithTelemetry() }),
    DOCS("#docs", { LinkTelemetry.BASE_DOCS_PAGE.browseWithTelemetry() })
}

class WalkthroughPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)
    private val pages = listOf(
        welcomePageData(),
        learnAsYouCodePageData(),
        connectWithYourTeamPageData(),
        reachOutToUsPageData()
    )
    private val previousButton = JButton("Previous").apply {
        isVisible = false
    }
    private val nextButton = JButton()
    private var currentPageIndex = 0

    init {
        preferredSize = Dimension(300, 20)
        pages.forEach { pageData ->
            cardPanel.add(createPagePanel(pageData), pageData.step)
        }

        val mainPanel = JBPanel<WalkthroughPanel>(BorderLayout()).apply {
            add(cardPanel, BorderLayout.CENTER)
            add(createNavigationPanel(), BorderLayout.SOUTH)
        }

        setContent(mainPanel)
    }

    private fun createPagePanel(pageData: PageData): JPanel {
        val imageLabel = JBLabel(pageData.icon)
        val stepLabel = JBLabel(pageData.step).apply {
            border = BorderFactory.createEmptyBorder(2, 8, 2, 0)
            font = font.deriveFont(Font.PLAIN, 14f)
        }
        val titleLabel = JBLabel(pageData.title).apply {
            border = BorderFactory.createEmptyBorder(2, 8, 2, 0)
            font = font.deriveFont(Font.BOLD, 16f)
        }
        val textLabel = SwingHelper.createHtmlViewer(false, font, null, null).apply {
            text = pageData.text
            border = JBUI.Borders.empty(8)
            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    WalkthroughActions.values().find { it.id == e.description }?.action?.invoke(project)
                }
            }
        }

        val centerPanel = JBPanel<WalkthroughPanel>(GridBagLayout())
        val gbc = GridBagConstraints(
            0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
            JBUI.emptyInsets(), 0, 0
        )

        centerPanel.add(stepLabel, gbc)
        gbc.gridy = 1
        centerPanel.add(titleLabel, gbc)
        gbc.apply {
            gridy = 2
            fill = GridBagConstraints.BOTH
            weightx = 1.0
            weighty = 1.0
        }
        centerPanel.add(textLabel, gbc)

        return JBPanel<WalkthroughPanel>(BorderLayout()).apply {
            add(imageLabel, BorderLayout.NORTH)
            add(centerPanel, BorderLayout.CENTER)
        }
    }

    private fun createNavigationPanel(): JBPanel<WalkthroughPanel> {
        updateNavigationButtons(0)
        previousButton.apply {
            addActionListener {
                updateNavigationButtons(-1)
                cardLayout.previous(cardPanel)
            }
        }
        nextButton.apply {
            addActionListener {
                if (currentPageIndex == pages.size - 1) {
                    getService(project, SonarLintWalkthroughToolWindow::class.java).hide()
                } else {
                    updateNavigationButtons(1)
                    cardLayout.next(cardPanel)
                }
            }
        }

        return JBPanel<WalkthroughPanel>(BorderLayout()).apply {
            add(previousButton, BorderLayout.WEST)
            add(nextButton, BorderLayout.EAST)
        }
    }

    private fun updateNavigationButtons(pageUpdate: Int) {
        currentPageIndex += pageUpdate

        previousButton.isVisible = currentPageIndex > 0
        nextButton.text = if (currentPageIndex < pages.size - 1) {
            "Next: ${pages[currentPageIndex + 1].title}"
        } else {
            "Close"
        }
    }

    private fun welcomePageData(): PageData {
        return PageData(
            "Step 1/4",
            "Get started",
            text = """
                SonarQube for IDE supports the local analysis of more than 18 languages including
                Python, Java, Javascript, IaC domains, and secrets detection.
                <a href="${WalkthroughActions.RULE_LINK.id}">Learn more</a>.<br><br>
                Detect issues on the fly in your open file while you code in the code editor. And run an analysis on multiple 
                files from the <a href="${WalkthroughActions.REPORT_VIEW.id}">Report tab</a>.<br><br>
                Open a file to start your Clean Code journey.
            """.trimIndent(),
            SonarLintIcons.WALKTHROUGH_WELCOME
        )
    }

    private fun learnAsYouCodePageData(): PageData {
        return PageData(
            "Step 2/4",
            "Learn as You Code",
            text = """
                Take a look at the <a href="${WalkthroughActions.CURRENT_FILE.id}">Current File tab</a>. 
                When SonarQube for IDE finds an issue, select it to 
                get its rule description, see an explanation of the issue, and get tips about how to fix it. 
                Check the documentation to learn how to <a href="${WalkthroughActions.INVESTIGATING_ISSUES_LINK.id}">investigate issues</a>.
                <br><br>
                Double-click an issue in the Report tab to open its location in the Editor. 
                Some rules offer quick fixes when you hover over the issue location.<br><br>
                If needed, you can disable rules in the <a href="${WalkthroughActions.SETTINGS.id}">Settings</a>.
            """.trimIndent(),
            SonarLintIcons.WALKTHROUGH_LEARN_AS_YOU_CODE
        )
    }

    private fun connectWithYourTeamPageData(): PageData {
        return PageData(
            "Step 3/4",
            "Connect with your team",
            text = """
                SonarQube for IDE can connect to SonarQube (Server, Cloud) using connected mode;
                this allows your entire team to use the same rules and applied quality standards.<br><br>
                When using <a href="${WalkthroughActions.CONNECTED_MODE_LINK.id}">connected mode</a>, 
                you can highlight advanced issues like <a href="${WalkthroughActions.TAINT_VULNERABILITIES.id}">taint vulnerabilities</a> 
                and analyze more languages that aren’t available with a local analysis. SonarQube (Server, Cloud) can also generate
                <a href="${WalkthroughActions.AI_FIX_SUGGESTIONS.id}">AI fix suggestions</a>, 
                and you can <a href="${WalkthroughActions.OPEN_IN_IDE_LINK.id}">open them right in your IDE!</a><br><br>
                Are you already using SonarQube (Server, Cloud)? <a href="${WalkthroughActions.SETUP_CONNECTION.id}">Click here</a> to set up a connection!
            """.trimIndent(),
            SonarLintIcons.WALKTHROUGH_CONNECT_WITH_YOUR_TEAM
        )
    }

    private fun reachOutToUsPageData(): PageData {
        return PageData(
            "Step 4/4",
            "Reach out to us",
            text = """
                Are you having problems with SonarQube for IDE? Open the <a href="${WalkthroughActions.LOG_VIEW.id}">log tab</a> 
                and enable the <a href="${WalkthroughActions.TROUBLESHOOTING_LINK.id}">Analysis logs and Verbose output</a>.<br><br>
                Share your verbose logs with us in a post on the <a href="${WalkthroughActions.COMMUNITY_FORUM.id}">Community</a> forum.
                We are happy to help you debug!<br><br>
            """.trimIndent(),
            SonarLintIcons.WALKTHROUGH_REACH_OUT_TO_US
        )
    }

    data class PageData(val step: String, val title: String, val text: String, val icon: Icon)
}
