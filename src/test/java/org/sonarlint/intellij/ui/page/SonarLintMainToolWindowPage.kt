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
import org.sonarlint.intellij.ui.ToolWindowConstants.FINDINGS_TAB_TITLE
import org.sonarlint.intellij.ui.currentfile.FindingsPanel

class SonarLintMainToolWindowPage private constructor(
    val findings: FindingsTabPage,
) {

    companion object {
        fun create(project: Project): SonarLintMainToolWindowPage {
            val toolWindow = ensureMainSonarLintToolWindow(project)
            val panel = toolWindow.contentManager.findContent(FINDINGS_TAB_TITLE)?.component
            check(panel is FindingsPanel) { "Findings tab is not a FindingsPanel" }
            return SonarLintMainToolWindowPage(FindingsTabPage(project, panel))
        }
    }
}
