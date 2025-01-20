/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout

class ReportTabStatusPanel internal constructor(private val project: Project) : JBPanel<ReportTabStatusPanel>(BorderLayout()) {
    companion object {
        private const val HELP_TEXT = "This view shows a snapshot of issues found after a manually triggered analysis. "
    }

    init {
        createPanel()
    }

    private fun createPanel() {
        add(WhatsInThisViewPanel(project, HELP_TEXT).panel, BorderLayout.EAST)
    }
}
