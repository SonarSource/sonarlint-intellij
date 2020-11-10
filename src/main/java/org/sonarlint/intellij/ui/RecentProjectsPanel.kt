/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
package org.sonarlint.intellij.ui

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.ProjectGroupActionGroup
import com.intellij.ide.RecentProjectListActionProvider
import com.intellij.ide.ReopenProjectAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.impl.welcomeScreen.BottomLineBorder
import com.intellij.ui.ClickListener
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.speedSearch.ListWithFilter
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.border.LineBorder
import kotlin.math.min

const val TITLE = "Pick Opened or Recent Project"
val preferredScrollableViewportSize = JBUI.size(350, 250)

open class SonarLintRecentProjectPanel(private val onProjectSelected: (Project) -> Unit) : JPanel(BorderLayout()) {

    private val projectsList: JBList<AnAction>

    private fun performSelectedAction(event: InputEvent, selection: AnAction): AnAction {
        val actionEvent = AnActionEvent
                .createFromInputEvent(event, ActionPlaces.POPUP, selection.templatePresentation,
                        DataManager.getInstance().getDataContext(projectsList), false, false)
        ActionUtil.performActionDumbAwareWithCallbacks(selection, actionEvent, actionEvent.dataContext)

        val openedProject = ProjectManager.getInstance().openProjects.firstOrNull { it.basePath == (selection as ReopenProjectAction).projectPath }
        openedProject?.let { onProjectSelected(it) }
        return selection
    }

    private fun addMouseMotionListener() {
        val mouseAdapter: MouseAdapter = object : MouseAdapter() {
            var myIsEngaged = false
            override fun mouseMoved(e: MouseEvent) {
                val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                if (focusOwner == null) {
                    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown { IdeFocusManager.getGlobalInstance().requestFocus(projectsList, true) }
                }
                if (projectsList.selectedIndices.size > 1) {
                    return
                }
                if (!myIsEngaged || UIUtil.isSelectionButtonDown(e) || focusOwner is JRootPane) {
                    myIsEngaged = true
                    return
                }
                val point = e.point
                val index = projectsList.locationToIndex(point)
                projectsList.selectedIndex = index
                val cellBounds = projectsList.getCellBounds(index, index)
                if (cellBounds != null && cellBounds.contains(point)) {
                    UIUtil.setCursor(projectsList, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                    projectsList.repaint(cellBounds)
                } else {
                    UIUtil.setCursor(projectsList, Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
                    projectsList.repaint()
                }
            }

            override fun mouseExited(e: MouseEvent) {
                projectsList.repaint()
            }
        }
        projectsList.addMouseMotionListener(mouseAdapter)
        projectsList.addMouseListener(mouseAdapter)
    }

    private inner class ProjectsList constructor(val requestedSize: Dimension, listData: Collection<AnAction>) : JBList<AnAction>(listData) {

        override fun getPreferredScrollableViewportSize() = requestedSize

        init {
            setExpandableItemsEnabled(false)
            setEmptyText(IdeBundle.message("empty.text.no.project.open.yet"))
            selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            getAccessibleContext().accessibleName = TITLE
        }
    }

    private inner class RecentProjectItemRenderer : JPanel(VerticalFlowLayout()), ListCellRenderer<AnAction> {
        private val myName = JLabel()
        private val myPath = JLabel()

        private fun layoutComponents() {
            add(myName)
            add(myPath)
        }

        private fun getListBackground(isSelected: Boolean): Color {
            return UIUtil.getListBackground(isSelected, true)
        }

        private fun getListForeground(isSelected: Boolean): Color {
            return UIUtil.getListForeground(isSelected, true)
        }

        override fun getListCellRendererComponent(list: JList<out AnAction>, value: AnAction, index: Int, selected: Boolean, focused: Boolean): Component {
            val fore = getListForeground(selected)
            val back = getListBackground(selected)
            myName.foreground = fore
            myPath.foreground = if (selected) fore else UIUtil.getInactiveTextColor()
            background = back
            if (value is ReopenProjectAction) {
                myName.text = value.templatePresentation.text
                myPath.text = getSubtitle(value)
            } else if (value is ProjectGroupActionGroup) {
                myName.text = value.group.name
                myPath.text = ""
            }
            AccessibleContextUtil.setCombinedName(this, myName, " - ", myPath)
            AccessibleContextUtil.setCombinedDescription(this, myName, " - ", myPath)
            return this
        }

        private fun getSubtitle(action: ReopenProjectAction): String? {
            var fullText = action.projectPath
            if (fullText == null || fullText.isEmpty()) return " "
            fullText = FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(fullText), false)
            return fullText
        }

        override fun getPreferredSize(): Dimension {
            val size = super.getPreferredSize()
            return Dimension(min(size.width, JBUI.scale(245)), size.height)
        }

        override fun getSize() = preferredSize

        init {
            myPath.font = JBUI.Fonts.label(if (SystemInfo.isMac) 10f else 11f)
            isFocusable = true
            layoutComponents()
        }
    }

    init {
        var recentProjectActions = RecentProjectListActionProvider.getInstance().getActions()
        recentProjectActions = recentProjectActions.filter { it is ReopenProjectAction && isPathValid(it.projectPath) }
        projectsList = ProjectsList(preferredScrollableViewportSize, recentProjectActions)
        projectsList.cellRenderer = RecentProjectItemRenderer()
        object : ClickListener() {
            override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                val selectedIndex = projectsList.selectedIndex
                if (selectedIndex >= 0) {
                    val cellBounds = projectsList.getCellBounds(selectedIndex, selectedIndex)
                    if (cellBounds.contains(event.point)) {
                        val selection = projectsList.selectedValue
                        if (selection != null) {
                            performSelectedAction(event, selection)
                        }
                    }
                }
                return true
            }
        }.installOn(projectsList)
        projectsList.registerKeyboardAction({ e ->
            val selectedValues = projectsList.selectedValuesList
            if (selectedValues != null) {
                for (selectedAction in selectedValues) {
                    if (selectedAction != null) {
                        val event: InputEvent = KeyEvent(projectsList, KeyEvent.KEY_PRESSED, e.getWhen(), e.modifiers, KeyEvent.VK_ENTER, '\r')
                        performSelectedAction(event, selectedAction)
                    }
                }
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        addMouseMotionListener()
        projectsList.selectedIndex = 0
        val scroll = JBScrollPane(projectsList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
        scroll.border = null
        val list = if (recentProjectActions.isEmpty()) projectsList else ListWithFilter.wrap(projectsList, scroll) { o: AnAction ->
            if (o is ReopenProjectAction) {
                val home = SystemProperties.getUserHome()
                var path = o.projectPath
                if (FileUtil.startsWith(path, home)) {
                    path = path.substring(home.length)
                }
                return@wrap o.projectName + " " + path
            } else if (o is ProjectGroupActionGroup) {
                return@wrap o.group.name
            }
            o.toString()
        }
        addComponents(list)
        border = LineBorder(Colors.BORDER_COLOR)
    }

    private fun isPathValid(pathStr: String): Boolean {
        val path = Paths.get(pathStr)
        val pathRoot = path.root ?: return false
        if (SystemInfo.isWindows && pathRoot.toString().startsWith("\\\\")) return true
        for (fsRoot in pathRoot.fileSystem.rootDirectories) {
            if (pathRoot == fsRoot) return Files.exists(path)
        }
        return false
    }

    private fun addComponents(list: JComponent) {
        add(list, BorderLayout.CENTER)
        add(createTitle(), BorderLayout.NORTH)
    }

    protected fun createTitle(): JPanel {
        val title: JPanel = object : JPanel() {
            override fun getPreferredSize(): Dimension {
                return Dimension(super.getPreferredSize().width, JBUI.scale(28))
            }
        }
        title.border = BottomLineBorder()
        val titleLabel = JLabel(TITLE)
        title.add(titleLabel)
        titleLabel.horizontalAlignment = SwingConstants.CENTER
        titleLabel.foreground = Colors.CAPTION_FOREGROUND
        title.background = Colors.CAPTION_BACKGROUND
        return title
    }
}

object Colors {
    // This is for border around recent projects, action cards and also lines separating header and footer from main contents.
    val BORDER_COLOR: Color = JBColor.namedColor("WelcomeScreen.borderColor", JBColor(Gray._190, Gray._85))

    // There two are for caption of Recent Project and Action Cards
    val CAPTION_BACKGROUND: Color = JBColor.namedColor("WelcomeScreen.captionBackground", JBColor(Gray._210, Gray._75))
    val CAPTION_FOREGROUND: Color = JBColor.namedColor("WelcomeScreen.captionForeground", JBColor(Gray._0, Gray._197))
}
