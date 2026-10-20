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
package org.sonarlint.intellij.fixtures

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import javax.swing.Icon

/**
 * Headless light-test harness for tool windows. Plugin tool windows from `plugin.xml` are not
 * registered in [ToolWindowHeadlessManagerImpl]; pass production [ToolWindowFactory] implementations.
 */
class HeadlessToolWindowHarness(private val project: Project) {

    fun registerToolWindowIfAbsent(
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

    fun ensureFactoryContentCreated(
        toolWindow: ToolWindow,
        factory: ToolWindowFactory,
        contentTitle: String,
        maxEdtDispatches: Int = 200,
    ) {
        if (toolWindow.contentManager.findContent(contentTitle) != null) {
            return
        }
        factory.createToolWindowContent(project, toolWindow)
        repeat(maxEdtDispatches) {
            UIUtil.dispatchAllInvocationEvents()
            if (toolWindow.contentManager.findContent(contentTitle) != null) {
                return
            }
        }
        error("ToolWindowFactory did not create content '$contentTitle' on EDT")
    }

    fun ensureFactoryContentCreated(
        toolWindow: ToolWindow,
        factory: ToolWindowFactory,
        hasContent: (ToolWindow) -> Boolean,
        maxEdtDispatches: Int = 200,
    ) {
        if (hasContent(toolWindow)) {
            return
        }
        factory.createToolWindowContent(project, toolWindow)
        repeat(maxEdtDispatches) {
            UIUtil.dispatchAllInvocationEvents()
            if (hasContent(toolWindow)) {
                return
            }
        }
        error("ToolWindowFactory did not create expected content on EDT")
    }

    fun teardownLightUi(vararg toolWindowIds: String) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles.toList().forEach { fileEditorManager.closeFile(it) }
        val manager = ToolWindowManager.getInstance(project)
        toolWindowIds.forEach { id ->
            manager.getToolWindow(id)?.contentManager?.removeAllContents(true)
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        val editorFactory = EditorFactory.getInstance()
        editorFactory.allEditors.forEach { editorFactory.releaseEditor(it) }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
}
