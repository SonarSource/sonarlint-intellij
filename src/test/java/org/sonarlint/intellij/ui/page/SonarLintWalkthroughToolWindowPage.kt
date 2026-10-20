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
import org.sonarlint.intellij.ui.icons.SonarLintIcons
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughToolWindowFactory
import org.sonarlint.intellij.ui.walkthrough.WalkthroughPanel

const val WALKTHROUGH_TOOL_WINDOW_ID: String = "Welcome to SonarQube for IDE"

class SonarLintWalkthroughToolWindowPage private constructor(
    val content: WalkthroughPanel,
) {

    companion object {
        fun create(project: Project): SonarLintWalkthroughToolWindowPage {
            val toolWindow = registerToolWindowIfAbsent(
                project,
                WALKTHROUGH_TOOL_WINDOW_ID,
                SonarLintWalkthroughToolWindowFactory(),
                SonarLintIcons.WALKTHROUGH_TOOLWINDOW_ICON,
                ToolWindowAnchor.LEFT,
            )
            ensureFactoryContentCreated(
                project,
                toolWindow,
                SonarLintWalkthroughToolWindowFactory(),
                contentReady = { tw ->
                    tw.contentManager.contentCount > 0
                },
            )
            val component = toolWindow.contentManager.contents.single().component
            check(component is WalkthroughPanel) { "Walkthrough tab is not a WalkthroughPanel" }
            return SonarLintWalkthroughToolWindowPage(component)
        }
    }
}
