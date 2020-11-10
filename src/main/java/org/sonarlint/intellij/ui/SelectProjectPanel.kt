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

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.impl.FileChooserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel


class SelectProjectPanel(private val onProjectSelected: (Project) -> Unit) : JPanel() {

    init {
        val openProjectButton = JButton("Open or import")

        layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 5, 15, false, false)
        add(openProjectButton)
        add(JLabel("or"))
        add(SonarLintRecentProjectPanel(onProjectSelected))

        openProjectButton.addActionListener {
            val descriptor: FileChooserDescriptor = OpenProjectFileChooserDescriptor(false)

            FileChooser.chooseFile(descriptor, null, VfsUtil.getUserHomeDir()) { file: VirtualFile ->
                if (!descriptor.isFileSelectable(file)) {
                    val message = IdeBundle.message("error.dir.contains.no.project", file.presentableUrl)
                    Messages.showInfoMessage(null as Project?, message, IdeBundle.message("title.cannot.open.project"))
                    return@chooseFile
                }
                val project = doOpenFile(file) ?: return@chooseFile
                onProjectSelected(project)
            }
        }

    }

    private fun doOpenFile(file: VirtualFile): Project? {
        return if (file.isDirectory)
            ProjectUtil.openOrImport(file.path, null, false)
        else null
    }
}

