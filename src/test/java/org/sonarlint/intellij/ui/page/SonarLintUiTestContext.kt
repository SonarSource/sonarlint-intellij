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

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID

class SonarLintUiTestContext(private val project: Project) {

    fun closeOpenEditorsAndSonarLintToolWindows() {
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles.toList().forEach { fileEditorManager.closeFile(it) }
        val toolWindows = ToolWindowManager.getInstance(project)
        for (id in listOf(TOOL_WINDOW_ID, WALKTHROUGH_TOOL_WINDOW_ID)) {
            toolWindows.getToolWindow(id)?.let { window ->
                window.contentManager.removeAllContents(true)
                toolWindows.unregisterToolWindow(id)
            }
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
}
