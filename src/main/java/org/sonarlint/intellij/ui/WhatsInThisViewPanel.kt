/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.CardLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.ui.CurrentFileStatusPanel.subscribeToEventsThatAffectCurrentFile
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.HelpLabelUtils.Companion.createHelpText
import org.sonarlint.intellij.util.HelpLabelUtils.Companion.createHelpTextNotConnected
import org.sonarlint.intellij.util.runOnPooledThread

class WhatsInThisViewPanel(val project: Project, private var helpText: String) {
    var panel: JPanel
    var layout = CardLayout()

    init {
        panel = JPanel(layout)
        createPanel()
        switchCards()
        runOnUiThread(project) { subscribeToEventsThatAffectCurrentFile(project) { this.switchCards() } }
    }

    private fun createPanel() {
        val notConnectedCardWrapper = JPanel(GridBagLayout())
        val connectedCardWrapper = JPanel(GridBagLayout())
        val helpLabelConnected = createHelpText(helpText)
        val helpLabelNotConnected = createHelpTextNotConnected(helpText)

        val gcs = GridBagConstraints(
            GridBagConstraints.CENTER, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
            JBUI.insets(2, 5, 2, 0), 0, 0
        )

        val helpLabelTextConnected = JLabel("What's in this view")
        val helpLabelTextNotConnected = JLabel("What's in this view")

        notConnectedCardWrapper.add(helpLabelTextNotConnected)
        notConnectedCardWrapper.add(helpLabelNotConnected, gcs)
        connectedCardWrapper.add(helpLabelTextConnected)
        connectedCardWrapper.add(helpLabelConnected, gcs)

        panel.add(notConnectedCardWrapper, NOT_CONNECTED)
        panel.add(connectedCardWrapper, CONNECTED)
        layout.show(panel, NOT_CONNECTED)
    }

    private fun switchCards() {
        ApplicationManager.getApplication().assertIsDispatchThread()

        // Checking connected mode state may take time, so lets move from EDT to pooled thread
        runOnPooledThread(project) {
            val projectBindingManager =
                SonarLintUtils.getService(project, ProjectBindingManager::class.java)
            projectBindingManager.tryGetServerConnection().ifPresentOrElse(
                {
                    switchCard(CONNECTED)
                }
            )  // No connection settings for project
            { switchCard(NOT_CONNECTED) }
        }

    }

    private fun switchCard(cardName: String) {
        runOnUiThread(project) { layout.show(panel, cardName) }
    }

    companion object {
        private const val NOT_CONNECTED = "Not Connected"
        private const val CONNECTED = "Connected"
    }
}
