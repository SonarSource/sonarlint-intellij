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
import com.intellij.ui.components.JBTextField
import javax.swing.JDialog
import org.sonarlint.intellij.fixtures.findComponents
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarlint.intellij.ui.page.settings.ModalDialogSession
import org.sonarlint.intellij.ui.page.settings.findOkButton
import org.sonarlint.intellij.ui.page.settings.findRadioButton

/** Page object for **Add SonarQube for IDE File Exclusion**. */
class AddProjectFileExclusionDialogPage internal constructor(
    private val dialog: JDialog,
    private val session: ModalDialogSession,
) {

    fun selectFile() {
        readOnEdt {
            dialog.findRadioButton("Exclude file").isSelected = true
        }
    }

    fun selectDirectory() {
        readOnEdt {
            dialog.findRadioButton("Exclude directory").isSelected = true
        }
    }

    fun selectGlobPattern() {
        readOnEdt {
            dialog.findRadioButton("GLOB").isSelected = true
        }
    }

    fun enterPath(path: String) {
        readOnEdt {
            activePathField().text = path
        }
    }

    fun confirm() {
        readOnEdt {
            dialog.findOkButton().doClick()
        }
        session.awaitClosed()
    }

    private fun activePathField(): JBTextField {
        val browseFields = dialog.rootPane.findComponents<TextFieldWithBrowseButton>()
        if (browseFields.isNotEmpty()) {
            val fileField = browseFields.first()
            if (fileField.isEnabled) {
                return fileField.textField as JBTextField
            }
            return browseFields.last().textField as JBTextField
        }
        return dialog.rootPane.findComponents<JBTextField>().first { it.isEnabled }
    }
}
