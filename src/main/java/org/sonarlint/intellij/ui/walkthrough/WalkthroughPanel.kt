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
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBPanel
import java.awt.CardLayout
import java.awt.Dimension
import javax.swing.JButton
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.PAGE_1
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.PAGE_2
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.PAGE_3
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughUtils.PAGE_4

class WalkthroughPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private var welcomePageNextButton: JButton
    private var learnAsYouCodePageBackButton: JButton
    private var learnAsYouCodePageNextButton: JButton
    private var connectWithYourTeamBackButton: JButton
    private var connectWithYourTeamNextButton: JButton
    private var reachOutToUsBackButton: JButton
    private var closeButton: JButton

    init {
        preferredSize = Dimension(SonarLintWalkthroughUtils.WIDTH, SonarLintWalkthroughUtils.HEIGHT)

        val mainPanel = JBPanel<JBPanel<*>>(CardLayout())
        mainPanel.preferredSize =
            Dimension(SonarLintWalkthroughUtils.WIDTH, SonarLintWalkthroughUtils.HEIGHT)

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
            mainPanel
        )

        super.setContent(mainPanel)
    }

    private fun addButtonActionListeners(
        mainPanel: JBPanel<JBPanel<*>>,
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
            getService(project, SonarLintWalkthroughToolWindow::class.java).hide()
        }
    }

}
