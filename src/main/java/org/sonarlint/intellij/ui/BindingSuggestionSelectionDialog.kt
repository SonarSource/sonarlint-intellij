/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import javax.swing.JList
import org.sonarlint.intellij.config.global.ServerConnectionService
import org.sonarlint.intellij.notifications.binding.BindingSuggestion

open class BindingSuggestionSelectionDialog(bindingSuggestions: List<BindingSuggestion>) :
    DialogWrapper(true) {
    private val suggestionsList = JBList(bindingSuggestions)

    open fun chooseSuggestion(): BindingSuggestion? {
        init()
        suggestionsList.cellRenderer = BindingSuggestionRenderer()

        title = "Bind the project"
        okAction.isEnabled = false
        suggestionsList.addListSelectionListener { okAction.isEnabled = true }
        val accepted = showAndGet()
        return if (accepted) suggestionsList.selectedValue else null
    }

    override fun createActions() = arrayOf(okAction, cancelAction)

    override fun createCenterPanel() = suggestionsList
}

class BindingSuggestionRenderer :
    ColoredListCellRenderer<BindingSuggestion>() {

    override fun customizeCellRenderer(
        list: JList<out BindingSuggestion>,
        suggestion: BindingSuggestion,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        val attrs = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        ServerConnectionService.getInstance().getServerConnectionByName(suggestion.connectionId)
            .ifPresentOrElse({
                icon = it.product.icon
                append(suggestion.projectName, attrs, true)
                append(" (" + suggestion.projectKey + ")", SimpleTextAttributes.GRAY_ATTRIBUTES, false)
                append(" on connection [" + suggestion.connectionId + "]", SimpleTextAttributes.GRAY_ATTRIBUTES, false)
            }, { append("Outdated suggestion", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES) })

    }

}
