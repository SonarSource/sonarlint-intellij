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
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBPanel
import java.awt.CardLayout
import java.awt.Dimension
import javax.swing.JButton
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.HEIGHT
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.PAGE_1
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.PAGE_2
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.PAGE_3
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.PAGE_4
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.WIDTH

class SonarLintWalkthroughToolWindowFactory : ToolWindowFactory{
    private lateinit var welcomePageNextButton: JButton
    private lateinit var learnAsYouCodePageBackButton: JButton
    private lateinit var learnAsYouCodePageNextButton: JButton
    private lateinit var connectWithYourTeamBackButton: JButton
    private lateinit var connectWithYourTeamNextButton: JButton
    private lateinit var reachOutToUsBackButton: JButton
    private lateinit var closeButton: JButton

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val mainPanel = JBPanel<JBPanel<*>>(CardLayout())
        mainPanel.preferredSize =
            Dimension(WIDTH, HEIGHT)

        val welcomePanel = WelcomePanel(project)
        val learnAsYouCodePanel = LearnAsYouCodePanel(project)
        val connectWithYourTeamPanel = ConnectWithYourTeamPanel(project)
        val reachOutToUsPanel = ReachOutToUsPanel(project)

        welcomePageNextButton = welcomePanel.nextButton
        learnAsYouCodePageBackButton = learnAsYouCodePanel.backButton
        learnAsYouCodePageNextButton = learnAsYouCodePanel.nextButton
        connectWithYourTeamBackButton = connectWithYourTeamPanel.backButton
        connectWithYourTeamNextButton = connectWithYourTeamPanel.nextButton
        reachOutToUsBackButton = reachOutToUsPanel.backButton
        closeButton = reachOutToUsPanel.closeButton

        mainPanel.add(welcomePanel, PAGE_1)
        mainPanel.add(learnAsYouCodePanel, PAGE_2)
        mainPanel.add(connectWithYourTeamPanel, PAGE_3)
        mainPanel.add(reachOutToUsPanel, PAGE_4)

        addButtonActionListeners(
            mainPanel,
            toolWindow
        )

        val content = toolWindow.contentManager.factory.createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun addButtonActionListeners(
        mainPanel: JBPanel<JBPanel<*>>, toolWindow: ToolWindow
    ) {
        welcomePageNextButton.addActionListener {
            val cl = mainPanel.layout as CardLayout
            cl.show(mainPanel, PAGE_2)
        }

        learnAsYouCodePageBackButton.addActionListener {
            val cl = mainPanel.layout as CardLayout
            cl.show(mainPanel, PAGE_1)
        }

        learnAsYouCodePageNextButton.addActionListener {
            val cl = mainPanel.layout as CardLayout
            cl.show(mainPanel, PAGE_3)
        }

        connectWithYourTeamBackButton.addActionListener {
            val cl = mainPanel.layout as CardLayout
            cl.show(mainPanel, PAGE_2)
        }

        connectWithYourTeamNextButton.addActionListener {
            val cl = mainPanel.layout as CardLayout
            cl.show(mainPanel, PAGE_4)
        }

        reachOutToUsBackButton.addActionListener {
            val cl = mainPanel.layout as CardLayout
            cl.show(mainPanel, PAGE_3)
        }

        closeButton.addActionListener {
            toolWindow.hide(null)
        }
    }
}

