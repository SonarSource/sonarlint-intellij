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
import com.intellij.util.ui.UIUtil
import javax.swing.Icon

internal fun registerToolWindowIfAbsent(
    project: Project,
    toolWindowId: String,
    factory: ToolWindowFactory,
    icon: Icon,
    anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM,
): ToolWindow {
    val manager = ToolWindowManager.getInstance(project)
    return manager.getToolWindow(toolWindowId)
        ?: manager.registerToolWindow(
            RegisterToolWindowTask.lazyAndClosable(toolWindowId, factory, icon, anchor),
        )
}

internal fun ensureFactoryContentCreated(
    project: Project,
    toolWindow: ToolWindow,
    factory: ToolWindowFactory,
    contentReady: (ToolWindow) -> Boolean,
    maxEdtDispatches: Int = 200,
) {
    if (contentReady(toolWindow)) {
        return
    }
    factory.createToolWindowContent(project, toolWindow)
    repeat(maxEdtDispatches) {
        UIUtil.dispatchAllInvocationEvents()
        if (contentReady(toolWindow)) {
            return
        }
    }
    error("ToolWindowFactory did not create expected content on EDT")
}
