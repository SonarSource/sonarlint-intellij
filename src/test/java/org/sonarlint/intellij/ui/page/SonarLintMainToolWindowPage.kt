/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.ui.page

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.ui.content.Content
import java.awt.Container
import org.sonarlint.intellij.fixtures.HeadlessToolWindowHarness
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory
import org.sonarlint.intellij.ui.ToolWindowConstants
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID
import org.sonarlint.intellij.ui.currentfile.CurrentFilePanel
import org.sonarlint.intellij.ui.icons.SonarLintIcons

class SonarLintMainToolWindowPage private constructor(
    private val project: Project,
    private val harness: HeadlessToolWindowHarness,
) {

    val currentFile: CurrentFileTabPage = CurrentFileTabPage { resolveCurrentFilePanel() }

    val currentFileToolbar: MainToolWindowToolbarPage =
        MainToolWindowToolbarPage { resolveFindingsTabRoot() }

    fun close() {
        harness.teardownLightUi(TOOL_WINDOW_ID)
    }

    private fun resolveFindingsTabRoot(): Container {
        val content = findingsContent()
        return content.component as Container
    }

    private fun resolveCurrentFilePanel(): CurrentFilePanel {
        val root = resolveFindingsTabRoot()
        check(root is CurrentFilePanel) { "Findings tab root is not a CurrentFilePanel: ${root.javaClass.name}" }
        return root
    }

    private fun findingsContent(): Content {
        val toolWindow = harness.registerToolWindowIfAbsent(
            TOOL_WINDOW_ID,
            SonarLintToolWindowFactory(),
            SonarLintIcons.SONARQUBE_FOR_INTELLIJ_EMPTY_TOOLWINDOW,
            ToolWindowAnchor.BOTTOM,
        )
        harness.ensureFactoryContentCreated(
            toolWindow,
            SonarLintToolWindowFactory(),
            ToolWindowConstants.CURRENT_FILE_TAB_TITLE,
        )
        return toolWindow.contentManager.findContent(ToolWindowConstants.CURRENT_FILE_TAB_TITLE)
            ?: error("Findings tab missing after SonarLintToolWindowFactory.createToolWindowContent")
    }

    companion object {
        fun open(project: Project): SonarLintMainToolWindowPage {
            val harness = HeadlessToolWindowHarness(project)
            val page = SonarLintMainToolWindowPage(project, harness)
            page.findingsContent()
            return page
        }
    }
}
