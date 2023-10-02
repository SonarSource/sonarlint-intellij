/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.GridBag
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JPanel
import org.sonarlint.intellij.actions.ShowLogAction
import org.sonarlint.intellij.cayc.CleanAsYouCodeService
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.common.util.SonarLintUtils.pluralize
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.persistence.FindingsCache

class SonarLintDashboard(private val editor: Editor) {

    companion object {
        private const val NO_FINDINGS_TEXT = "SonarLint found no findings, keep up the good job!"
        private const val CHECKBOX_TITLE = "Focus on new code"
    }

    val panel = JPanel(GridBagLayout())
    private val findingsSummaryLabel = JBLabel(NO_FINDINGS_TEXT)
    private val focusOnNewCodeCheckbox = JBCheckBox(CHECKBOX_TITLE)

    init {
        editor.project?.let { refreshCheckbox(it) }
        focusOnNewCodeCheckbox.addActionListener {
            getService(CleanAsYouCodeService::class.java).setFocusOnNewCode(focusOnNewCodeCheckbox.isSelected)
        }
        focusOnNewCodeCheckbox.isOpaque = false

        val presentation = Presentation()
        presentation.icon = AllIcons.Actions.More
        presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)

        val menuButton = ActionButton(
            MenuAction(),
            presentation,
            ActionPlaces.EDITOR_POPUP,
            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
        )

        val gc = GridBag().nextLine().next().anchor(GridBagConstraints.LINE_START).weightx(1.0).fillCellHorizontally().insets(10, 10, 10, 0)

        panel.add(findingsSummaryLabel, gc)
        panel.add(menuButton, gc.next().anchor(GridBagConstraints.LINE_END).weightx(0.0).insets(10, 6, 10, 6))
        panel.add(focusOnNewCodeCheckbox,
            gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1.0).insets(0, 10, 10, 0))
    }

    fun refresh() {
        val project = editor.project ?: return
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return

        refreshCheckbox(project)

        val findings = getService(project, FindingsCache::class.java).getFindingsForFile(file).filter { !it.isResolved }
        if (findings.isEmpty()) {
            findingsSummaryLabel.text = NO_FINDINGS_TEXT
        } else {
            val issuesCount = findings.filterIsInstance<LiveIssue>().size
            val hotspotsCount = findings.filterIsInstance<LiveSecurityHotspot>().size
            var text = "SonarLint found "
            if (issuesCount > 0) {
                text += issuesCount.toString() + pluralize(" issue", issuesCount.toLong())
            }
            if (hotspotsCount > 0) {
                if (issuesCount > 0) {
                    text += " and "
                }
                text += hotspotsCount.toString() + pluralize(" hotspot", hotspotsCount.toLong())
            }
            findingsSummaryLabel.text = text
        }
    }

    private fun refreshCheckbox(project: Project) {
        Settings.getSettingsFor(project).isBound.let {
            focusOnNewCodeCheckbox.isEnabled = it
            focusOnNewCodeCheckbox.text = if (it) CHECKBOX_TITLE else "$CHECKBOX_TITLE (connected mode only)"
        }
        val isFocusOnNewCode = Settings.getGlobalSettings().isFocusOnNewCode
        focusOnNewCodeCheckbox.isSelected = isFocusOnNewCode
    }

    private class MenuAction : DefaultActionGroup(), HintManagerImpl.ActionToIgnore {
        init {
            isPopup = true
            add(ActionManager.getInstance().getAction("SonarLint.toolwindow.Configure"))
            add(ShowLogAction())
        }
    }

}
