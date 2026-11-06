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
package org.sonarlint.intellij.ui.page.settings.project

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.uiDesigner.core.GridLayoutManager
import javax.swing.JComponent
import javax.swing.JTextField
import org.sonarlint.intellij.config.project.AddEditExclusionDialog
import org.sonarlint.intellij.config.project.ExclusionItem
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.ui.page.settings.findRadioButton

/** Page object for **Add SonarQube for IDE File Exclusion**. */
class AddProjectFileExclusionDialogPage internal constructor(
    private val exclusionDialog: AddEditExclusionDialog,
    private val onOk: (ExclusionItem) -> Unit,
) {
    /** Form root. Unit-test mode does not create a dialog window for [com.intellij.openapi.ui.DialogWrapper.showAndGet]. */
    private val content: JComponent
        get() {
            val field = AddEditExclusionDialog::class.java.getDeclaredField("panel")
            field.isAccessible = true
            return field.get(exclusionDialog) as JComponent
        }

    fun selectFile() = select("Exclude file")

    fun selectDirectory() = select("Exclude directory")

    fun selectGlobPattern() = select("GLOB")

    fun enterFilePath(path: String) = enter("Exclude file", path)

    fun enterDirectoryPath(path: String) = enter("Exclude directory", path)

    fun enterGlobPattern(pattern: String) = enter("GLOB", pattern)

    fun confirm() {
        readOnEdt {
            val item = exclusionDialog.exclusion
                ?: error("Exclusion dialog did not produce a value")
            check(item.item.isNotBlank()) { "Exclusion value is empty" }
            onOk(item)
        }
    }

    private fun select(label: String) {
        readOnEdt {
            content.findRadioButton(label).doClick()
        }
    }

    private fun enter(label: String, value: String) {
        readOnEdt {
            textFieldForRadio(label).text = value
        }
    }

    /** Field on the same grid row as the radio [label]. */
    private fun textFieldForRadio(label: String): JTextField {
        val radio = content.findRadioButton(label)
        val grid = radio.parent
        val layout = grid.layout as? GridLayoutManager
            ?: error("Exclusion dialog is not laid out on a grid")
        val row = layout.getConstraintsForComponent(radio).row
        val field = grid.components.first { component ->
            component !== radio && layout.getConstraintsForComponent(component).row == row
        }
        return when (field) {
            is TextFieldWithBrowseButton -> field.textField
            is JTextField -> field
            else -> error("No text field on the '$label' row")
        }
    }
}
