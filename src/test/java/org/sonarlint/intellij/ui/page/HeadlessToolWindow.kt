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
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.Icon
import org.sonarlint.intellij.fixtures.pumpEdtUntil
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory
import org.sonarlint.intellij.ui.ToolWindowConstants.FINDINGS_TAB_TITLE
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID
import org.sonarlint.intellij.ui.icons.SonarLintIcons
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughToolWindowFactory

internal fun ensureMainSonarLintToolWindow(project: Project): ToolWindow {
    val factory = SonarLintToolWindowFactory()
    return ensureHeadlessToolWindow(
        project,
        TOOL_WINDOW_ID,
        SonarLintIcons.SONARQUBE_FOR_INTELLIJ_EMPTY_TOOLWINDOW,
        ToolWindowAnchor.BOTTOM,
        factory,
        contentReady = { tw -> tw.contentManager.findContent(FINDINGS_TAB_TITLE) != null },
    )
}

internal fun ensureWalkthroughToolWindow(project: Project): ToolWindow {
    val factory = SonarLintWalkthroughToolWindowFactory()
    return ensureHeadlessToolWindow(
        project,
        WALKTHROUGH_TOOL_WINDOW_ID,
        SonarLintIcons.WALKTHROUGH_TOOLWINDOW_ICON,
        ToolWindowAnchor.LEFT,
        factory,
        contentReady = { tw -> tw.contentManager.contentCount > 0 },
    )
}

private fun ensureHeadlessToolWindow(
    project: Project,
    toolWindowId: String,
    icon: Icon,
    anchor: ToolWindowAnchor,
    factory: ToolWindowFactory,
    contentReady: (ToolWindow) -> Boolean,
): ToolWindow {
    val manager = ToolWindowManager.getInstance(project)
    val toolWindow = manager.getToolWindow(toolWindowId)
        ?: manager.registerToolWindow(
            RegisterToolWindowTask.lazyAndClosable(toolWindowId, factory, icon, anchor),
        )
    if (contentReady(toolWindow)) {
        return toolWindow
    }
    factory.createToolWindowContent(project, toolWindow)
    pumpEdtUntil { contentReady(toolWindow) }
    return toolWindow
}
