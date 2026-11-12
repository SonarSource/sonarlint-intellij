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

import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.Content
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.fixtures.walkComponents
import org.sonarlint.intellij.ui.tree.IssueTree
import org.sonarlint.intellij.ui.report.ReportPanel
import org.mockito.Mockito.spy
import org.mockito.Mockito.mockingDetails

class ReportInstancePage internal constructor(private val content: Content) {
    fun recordRefreshes(): () -> Int = readOnEdt {
        val panel = spy(content.component as ReportPanel)
        val count = { mockingDetails(panel).invocations.count { it.method.name == "refreshView" } }
        content.component = panel
        count
    }

    val title: String get() = readOnEdt { content.displayName }
    val isSelected: Boolean get() = readOnEdt { content.manager?.selectedContent === content }
    val messages: List<String> get() = readOnEdt {
        content.component.walkComponents().filterIsInstance<IssueTree>()
            .flatMap { FindingsTreePage(it).issues }.map { it.message }.toList()
    }
    val statusTexts: List<String> get() = readOnEdt {
        content.component.walkComponents().filterIsInstance<JBLabel>()
            .mapNotNull { it.text }.filter { it.startsWith("Analysis ongoing") }.toList()
    }
}
