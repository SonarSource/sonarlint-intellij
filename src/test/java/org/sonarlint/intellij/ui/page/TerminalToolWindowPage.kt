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

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.awt.event.HierarchyEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.fixtures.readOnEdt

/**
 * The IDE's Terminal tool window, where the plugin runs the SonarQube CLI commands that need the user.
 *
 * The Terminal plugin starts a tab's command only once the tab is first shown on screen, which never happens in the
 * headless sandbox. So every tab opened while this page is in use is shown right away, as the user would see it.
 */
class TerminalToolWindowPage private constructor(
    private val project: Project,
    private val toolWindow: ToolWindow,
    parentDisposable: Disposable,
) {

    private val openedTabs = CopyOnWriteArrayList<String>()
    private val openedTerminals = CopyOnWriteArrayList<TerminalWidget>()

    /** Names of every terminal tab opened since the page was created, e.g. "SonarQube CLI"; a tab may close itself when its command ends. */
    val openedTabNames: List<String>
        get() = openedTabs.toList()

    init {
        val showNewTabs = object : ContentManagerListener {
            override fun contentAdded(event: ContentManagerEvent) {
                openedTabs.add(event.content.displayName)
                // The manager links the tab to its terminal after adding it
                ApplicationManager.getApplication().invokeLater {
                    TerminalToolWindowManager.findWidgetByContent(event.content)?.let {
                        openedTerminals.add(it)
                        showOnScreen(it.component)
                    }
                }
            }
        }
        toolWindow.contentManager.addContentManagerListener(showNewTabs)
        Disposer.register(parentDisposable) { toolWindow.contentManager.removeContentManagerListener(showNewTabs) }
    }

    /** What the most recently opened terminal shows, empty before any terminal opens. */
    val lastTerminalText: String
        get() = readOnEdt { openedTerminals.lastOrNull()?.let { JBTerminalWidget.asJediTermWidget(it)?.text }.orEmpty() }

    /** Types [text] in the most recently opened terminal, e.g. "\r" to press Enter. */
    fun typeInLastTerminal(text: String) {
        val connector = checkNotNull(openedTerminals.lastOrNull()?.ttyConnector) { "No running terminal" }
        connector.write(text)
    }

    /** Closes every terminal tab, which also ends the commands still running in them. */
    fun closeAllTabs() {
        runInEdtAndWait {
            val manager = TerminalToolWindowManager.getInstance(project)
            toolWindow.contentManager.contents.toList().forEach { manager.closeTab(it) }
        }
        pumpEdt()
    }

    /**
     * Marks the tab as showing, then notifies its listeners like Swing does when a window appears. `UIUtil.markAsShowing`
     * does the first part only when `java.awt.headless` is set, which is not the case on every OS.
     */
    private fun showOnScreen(component: JComponent) {
        component.putClientProperty(checkNotNull(Key.findKeyByName(SHOWING_KEY_NAME)) { "No '$SHOWING_KEY_NAME' key" }, true)
        component.dispatchEvent(
            HierarchyEvent(component, HierarchyEvent.HIERARCHY_CHANGED, component, component.parent, HierarchyEvent.SHOWING_CHANGED.toLong())
        )
    }

    companion object {
        /** The client property `ComponentUtil.isShowing` checks on a component and its ancestors. */
        private const val SHOWING_KEY_NAME = "Component.isShowing"

        /** The page stops showing new tabs when [parentDisposable] is disposed. */
        fun create(project: Project, parentDisposable: Disposable) =
            TerminalToolWindowPage(project, ensureTerminalToolWindow(project), parentDisposable)
    }
}
