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
package org.sonarlint.intellij.fix

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.get
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.inlay.FixSuggestionInlayPanel
import org.sonarlint.intellij.ui.inlay.InlayManager
import org.sonarlint.intellij.util.getDocument
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.FixSuggestionDto

class ShowFixSuggestion(private val project: Project, private val file: VirtualFile, private val fixSuggestion: FixSuggestionDto) {

    fun show() {
        var hasNavigated = false

        runOnUiThread(project, ModalityState.defaultModalityState()) {
            val fileEditorManager = FileEditorManager.getInstance(project)
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@runOnUiThread
            val document = file.getDocument() ?: return@runOnUiThread

            if (!isWithinBounds(document)) {
                get(project).simpleNotification(
                    null,
                    "Unable to open the fix suggestion, your file has probably changed",
                    NotificationType.WARNING
                )
                return@runOnUiThread
            }

            var successfullyOpened = true

            fixSuggestion.fileEdit().changes().forEachIndexed { index, change ->
                val startLine = change.beforeLineRange().startLine
                val endLine = change.beforeLineRange().endLine

                if (!hasNavigated) {
                    val descriptor = OpenFileDescriptor(
                        project, file,
                        startLine - 1, -1
                    )

                    fileEditorManager.openTextEditor(
                        descriptor, true
                    )

                    hasNavigated = true

                    fileEditorManager.selectedTextEditor?.let {
                        val inlayManager = InlayManager.from(it)
                        inlayManager.dispose()
                    }

                }

                fileEditorManager.selectedTextEditor?.let {
                    val doc = it.document
                    try {
                        val rangeMarker = doc.createRangeMarker(doc.getLineStartOffset(startLine - 1), doc.getLineEndOffset(endLine - 1))
                        val currentCode = doc.getText(TextRange(rangeMarker.startOffset, rangeMarker.endOffset))
                        val fixSuggestionSnippet = FixSuggestionSnippet(
                            currentCode,
                            change.after(),
                            startLine,
                            endLine,
                            index + 1,
                            fixSuggestion.fileEdit().changes().size,
                            fixSuggestion.explanation(),
                            fixSuggestion.suggestionId()
                        )

                        FixSuggestionInlayPanel(
                            project,
                            fixSuggestionSnippet,
                            it,
                            psiFile,
                            rangeMarker
                        )
                    } catch (e: IndexOutOfBoundsException) {
                        SonarLintConsole.get(project).error("Fix is invalid", e)
                        successfullyOpened = false
                    }
                }
            }

            if (!successfullyOpened) {
                get(project).simpleNotification(
                    null,
                    "Unable to open the fix suggestion, your file has probably changed",
                    NotificationType.WARNING
                )
            } else if (isBeforeContentIdentical(document)) {
                get(project).simpleNotification(
                    null,
                    "The fix suggestion has been successfully opened",
                    NotificationType.INFORMATION
                )
            } else {
                get(project).simpleNotification(
                    null,
                    "The fix suggestion has been opened, but the file's content has changed, so it may not be applicable",
                    NotificationType.WARNING
                )
            }
        }
    }

    private fun isWithinBounds(document: Document): Boolean {
        return fixSuggestion.fileEdit().changes().all { change ->
            val lineStart = change.beforeLineRange().startLine
            val lineEnd = change.beforeLineRange().endLine

            return lineStart <= document.lineCount && lineEnd <= document.lineCount
        }
    }

    private fun isBeforeContentIdentical(document: Document): Boolean {
        return fixSuggestion.fileEdit().changes().all { change ->
            val lineStart = change.beforeLineRange().startLine
            val lineEnd = change.beforeLineRange().endLine

            val lineStartOffset = document.getLineStartOffset(lineStart)
            val lineEndOffset = document.getLineEndOffset(lineEnd)
            val documentBeforeCode = document.getText(TextRange(lineStartOffset, lineEndOffset))

            return documentBeforeCode == change.before()
        }
    }

}
