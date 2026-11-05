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
import java.awt.Component
import org.sonarlint.intellij.fixtures.walkComponents
import org.sonarlint.intellij.ui.tree.IssueTree

/** Collects user-visible strings from a tool-window panel (labels, tree summaries, issue messages). */
internal fun collectPanelDisplayTexts(root: Component): List<String> {
    val texts = linkedSetOf<String>()
    root.walkComponents().forEach { component ->
        when (component) {
            is JBLabel -> {
                if (component.isShowing) {
                    component.text?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
                }
            }
            is IssueTree -> {
                if (!component.isShowing) {
                    return@forEach
                }
                val treePage = FindingsTreePage(component)
                texts.add(treePage.summaryLabelText)
                treePage.issues.forEach { texts.add(it.message) }
            }
        }
    }
    return texts.toList()
}
