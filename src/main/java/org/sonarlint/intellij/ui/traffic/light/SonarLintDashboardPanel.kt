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
package org.sonarlint.intellij.ui.traffic.light

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.Editor
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import org.apache.commons.lang3.StringUtils
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.actions.RestartBackendAction.Companion.SONARLINT_ERROR_MSG
import org.sonarlint.intellij.actions.ShowLogAction
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.finding.FindingType.ISSUE
import org.sonarlint.intellij.finding.FindingType.SECURITY_HOTSPOT
import org.sonarlint.intellij.finding.FindingType.TAINT_VULNERABILITY
import org.sonarlint.intellij.util.HelpLabelUtils
import org.sonarlint.intellij.util.runOnPooledThread


class SonarLintDashboardPanel(private val editor: Editor) {

    companion object {
        private const val NO_FINDINGS_TEXT = "No problems found, keep up the good work!"
        private const val NO_CONNECTED_MODE_TITLE = "Connected mode is not active"
        private const val NO_BINDING_TITLE = "No binding found"
        private const val CHECKBOX_TITLE = "Focus on New Code"
    }

    val panel = JPanel(GridBagLayout())
    private val sonarlintCrashed = JBLabel(SONARLINT_ERROR_MSG)
    private val findingsSummaryLabel = JBLabel(NO_FINDINGS_TEXT)
    private val connectionIcon = JBLabel()
    private val connectionLabel = JBLabel(NO_CONNECTED_MODE_TITLE)
    private val connectionNameLabel = JBLabel()
    private val connectionHelp = HelpLabelUtils.createConnectedMode()
    private val bindingLabel = JBLabel(NO_BINDING_TITLE)
    private val focusOnNewCodeCheckbox = JBCheckBox(CHECKBOX_TITLE)

    private val connectedModePanel: JPanel
    private val focusPanel: JPanel
    private val restartPanel: JPanel

    init {
        editor.project?.let { project ->
            refreshCheckbox()
            focusOnNewCodeCheckbox.addActionListener {
                runOnPooledThread(project) { getService(CleanAsYouCodeService::class.java).setFocusOnNewCode(focusOnNewCodeCheckbox.isSelected) }
            }
        }
        focusOnNewCodeCheckbox.isOpaque = false

        val menuButton = ActionButton(
            MenuAction(),
            null,
            ActionPlaces.EDITOR_POPUP,
            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
        )

        val gc =
            GridBag().nextLine().next().anchor(GridBagConstraints.LINE_START).weightx(1.0).fillCellHorizontally().insets(10, 10, 10, 10)

        sonarlintCrashed.isVisible = false
        panel.add(sonarlintCrashed, gc)
        panel.add(findingsSummaryLabel, gc)
        panel.add(menuButton, gc.next().anchor(GridBagConstraints.LINE_END).weightx(0.0).insets(10, 6, 10, 6))
        connectedModePanel = JPanel(HorizontalLayout(5))
        connectedModePanel.add(connectionLabel)
        connectedModePanel.add(connectionIcon)
        connectedModePanel.add(connectionNameLabel)
        connectedModePanel.add(connectionHelp)
        panel.add(
            connectedModePanel,
            gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1.0).insets(0, 10, 10, 10)
        )
        panel.add(
            bindingLabel,
            gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1.0).insets(0, 10, 10, 10)
        )
        focusPanel = JPanel(HorizontalLayout(5))
        focusPanel.add(focusOnNewCodeCheckbox)
        focusPanel.add(HelpLabelUtils.createCleanAsYouCode())
        panel.add(
            focusPanel,
            gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1.0).insets(0, 10, 10, 10)
        )
        restartPanel = createLowerPanel()
        restartPanel.isVisible = false
        panel.add(
            restartPanel,
            gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1.0)
        )
    }

    private fun handleIfAlive(isAlive: Boolean) {
        restartPanel.isVisible = !isAlive
        sonarlintCrashed.isVisible = !isAlive

        findingsSummaryLabel.isVisible = isAlive
        connectedModePanel.isVisible = isAlive
        bindingLabel.isVisible = isAlive
        focusPanel.isVisible = isAlive
    }

    fun refresh(summary: SonarLintDashboardModel) {
        val project = editor.project ?: return
        handleIfAlive(summary.isAlive)
        refreshCheckbox()

        if (summary.findingsCount() == 0) {
            findingsSummaryLabel.text = NO_FINDINGS_TEXT
        } else {
            val fragments = mutableListOf<String>()
            with(summary) {
                if (issuesCount > 0) {
                    fragments.add(ISSUE.display(issuesCount))
                }
                if (hotspotsCount > 0) {
                    fragments.add(SECURITY_HOTSPOT.display(hotspotsCount))
                }
                if (taintVulnerabilitiesCountForFile > 0) {
                    fragments.add(TAINT_VULNERABILITY.display(taintVulnerabilitiesCountForFile))
                }
                findingsSummaryLabel.text = fragments.joinToString()
            }
        }

        val settings = Settings.getSettingsFor(project)
        settings.connectionName?.let { connectionName ->
            val serverConnection = getService(project, ProjectBindingManager::class.java).serverConnection

            connectionLabel.text = "Connected to:"
            connectionNameLabel.text = connectionName
            connectionNameLabel.isVisible = true
            connectionHelp.isVisible = false
            connectionIcon.isVisible = true
            if (serverConnection.isSonarCloud) {
                connectionIcon.icon = SonarLintIcons.ICON_SONARQUBE_CLOUD_16
            } else {
                connectionIcon.icon = SonarLintIcons.ICON_SONARQUBE_SERVER_16
            }
            settings.projectKey?.let { projectKey ->
                bindingLabel.isVisible = true
                bindingLabel.text = "Bound to project: ${StringUtils.abbreviate(projectKey, 100)}"
            } ?: run {
                bindingLabel.isVisible = false
                bindingLabel.text = NO_BINDING_TITLE
            }
        } ?: run {
            connectionLabel.text = NO_CONNECTED_MODE_TITLE
            connectionIcon.isVisible = false
            connectionNameLabel.isVisible = false
            connectionHelp.isVisible = true
            bindingLabel.isVisible = false
            bindingLabel.text = NO_BINDING_TITLE
        }
    }

    private fun refreshCheckbox() {
        focusOnNewCodeCheckbox.text = CHECKBOX_TITLE
        focusOnNewCodeCheckbox.isSelected = Settings.getGlobalSettings().isFocusOnNewCode
    }

    private fun createLowerPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gc = GridBag().nextLine()

        val constrains = gc.next()
        val noAccessLabel = HyperlinkLabel("Restart SonarQube for IntelliJ Service").apply {
            addHyperlinkListener(object : HyperlinkAdapter() {
                override fun hyperlinkActivated(e: HyperlinkEvent) {
                    getService(BackendService::class.java).restartBackendService()
                }
            })
        }
        panel.add(noAccessLabel, constrains)
        panel.add(Box.createHorizontalGlue(), gc.next().fillCellHorizontally().weightx(1.0))

        panel.isOpaque = true
        panel.background = UIUtil.getToolTipActionBackground()
        panel.border = JBUI.Borders.empty(4, 10)
        return panel
    }

    private class MenuAction : DefaultActionGroup(), HintManagerImpl.ActionToIgnore {
        init {
            add(ActionManager.getInstance().getAction("SonarLint.toolwindow.Configure"))
            add(ShowLogAction())
            templatePresentation.isPopupGroup = true
            templatePresentation.icon = AllIcons.Actions.More
            templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
        }
    }

}
