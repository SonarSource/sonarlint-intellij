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
package org.sonarlint.intellij.ui.page.settings

import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.CommonActionsPanel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import java.awt.Component
import javax.swing.JButton
import javax.swing.JRadioButton
import org.sonarlint.intellij.fixtures.findComponents
import org.sonarlint.intellij.fixtures.walkComponents

/** Outermost tab strip (walk order is top-down from the settings root). */
internal fun Component.findTabbedPane(): JBTabbedPane =
    walkComponents().filterIsInstance<JBTabbedPane>().first()

internal fun JBTabbedPane.selectTab(title: String) {
    val index = (0 until tabCount).first { getTitleAt(it) == title }
    selectedIndex = index
}

internal fun Component.selectedTabContent(): Component =
    findTabbedPane().selectedComponent ?: error("No tab selected")

/** First toolbar **Add** button ([ToolbarDecorator] order: add, remove, edit). */
internal fun Component.findToolbarAddButton(): Component {
    exclusionsToolbarHost()?.let { host ->
        host.walkComponents()
            .firstOrNull { it.javaClass.simpleName.endsWith("ActionButton") }
            ?.let { return it }
        host.walkComponents()
            .filterIsInstance<ActionToolbarImpl>()
            .firstOrNull()
            ?.let { toolbar ->
                toolbar.updateActionsImmediately()
                toolbar.walkComponents()
                    .firstOrNull { it.javaClass.simpleName.endsWith("ActionButton") }
                    ?.let { return it }
            }
    }
    walkComponents()
        .filterIsInstance<CommonActionsPanel>()
        .firstOrNull()
        ?.let { return it.resolveAddButton() }
    val sampleTypes = walkComponents().map { it.javaClass.simpleName }.distinct().take(40).toList()
    error("No toolbar add button in settings UI (sample component types: $sampleTypes)")
}

private fun Component.exclusionsToolbarHost(): Component? {
    walkComponents()
        .filterIsInstance<JBTable>()
        .firstOrNull { it.columnCount == 2 && it.getColumnName(0) == "Type" }
        ?.parent
        ?.let { return it }
    walkComponents()
        .filterIsInstance<JBList<*>>()
        .firstOrNull { list ->
            list.parent?.walkComponents()?.any { it is ActionToolbarImpl } == true
        }
        ?.parent
        ?.let { return it }
    return null
}

private fun CommonActionsPanel.resolveAddButton(): Component {
    javaClass.methods
        .asSequence()
        .filter { it.parameterCount == 0 && it.name.contains("Add", ignoreCase = true) }
        .mapNotNull { method -> method.invoke(this) as? Component }
        .firstOrNull()
        ?.let { return it }
    javaClass.declaredFields
        .asSequence()
        .filter { it.name.contains("Add", ignoreCase = true) }
        .mapNotNull { field ->
            field.isAccessible = true
            field.get(this) as? Component
        }
        .firstOrNull()
        ?.let { return it }
    error("Could not resolve add button on CommonActionsPanel")
}

internal fun Component.triggerToolbarButtonClick() {
    when (this) {
        is JButton -> doClick()
        else -> {
            val click = javaClass.methods.firstOrNull { it.name == "click" && it.parameterCount == 0 }
            if (click != null) {
                click.invoke(this)
            } else {
                error("Could not trigger ${javaClass.name}")
            }
        }
    }
}

internal fun Component.findRadioButton(labelContains: String): JRadioButton =
    findComponents<JRadioButton>().first { it.text.contains(labelContains, ignoreCase = true) }
