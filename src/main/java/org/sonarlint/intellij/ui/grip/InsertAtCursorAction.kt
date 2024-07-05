/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.ui.grip

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiManager
import org.sonarlint.intellij.actions.AbstractSonarAction

class InsertAtCursorAction(private val codeEditor: Editor) :
    AbstractSonarAction("Insert at cursor location", "Insert at cursor location", AllIcons.Actions.StepOutCodeBlock) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val mainEditor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val vFile = FileDocumentManager.getInstance().getFile(mainEditor.document) ?: return
        val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return
        WriteCommandAction.writeCommandAction(project, psiFile)
            .run<RuntimeException> {
                // Send to the backend the information on the feedback buttons (bad/ok/very good) and the comment message
                mainEditor.document.insertString(
                    mainEditor.caretModel.offset,
                    StringUtil.convertLineSeparators(codeEditor.document.text)
                )
            }
    }
}
