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
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.get
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.codefix.FixSuggestionInlayHolder
import org.sonarlint.intellij.ui.inlay.FixSuggestionInlayPanel
import org.sonarlint.intellij.ui.inlay.InlayManager
import org.sonarlint.intellij.util.getDocument
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.FixSuggestionDto

data class LocalFixSuggestion(
    val suggestionId: String,
    val explanation: String,
    val changes: List<FixChanges>,
)

data class FixChanges(
    val startLine: Int,
    val endLine: Int,
    val beforeCode: String?,
    val newCode: String
)

class ShowFixSuggestion(private val project: Project, private val file: VirtualFile) {

    fun show(fixSuggestion: FixSuggestionDto) {
        val localFixSuggestion = mapToLocalFixSuggestion(fixSuggestion)
        show(localFixSuggestion, true)
    }

    fun show(fixSuggestion: LocalFixSuggestion, firstTime: Boolean) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val psiFile = computeReadActionSafely(project) { PsiManager.getInstance(project).findFile(file) } ?: return
        val document = computeReadActionSafely(project) { file.getDocument() } ?: return

        if (!isWithinBounds(document, fixSuggestion.changes)) {
            get(project).simpleNotification(
                null, "Unable to open the fix suggestion, your file has probably changed", NotificationType.WARNING
            )
            return
        }

        var successfullyOpened = true

        runOnUiThread(project, ModalityState.defaultModalityState()) {
            fixSuggestion.changes.forEachIndexed { index, change ->
                if (!firstTime && !getService(project, FixSuggestionInlayHolder::class.java).shouldShowSnippet(fixSuggestion.suggestionId, index)) return@forEachIndexed

                if (index == 0) {
                    val descriptor = OpenFileDescriptor(project, file, change.startLine - 1, -1)

                    fileEditorManager.openTextEditor(descriptor, true)

                    fileEditorManager.selectedTextEditor?.let {
                        val inlayManager = InlayManager.from(it)
                        inlayManager.dispose()
                    }
                }

                fileEditorManager.selectedTextEditor?.let {
                    val doc = it.document
                    try {
                        val rangeMarker = doc.createRangeMarker(doc.getLineStartOffset(change.startLine - 1), doc.getLineEndOffset(change.endLine - 1))
                        val currentCode = doc.getText(TextRange(rangeMarker.startOffset, rangeMarker.endOffset))
                        val fixSuggestionSnippet = FixSuggestionSnippet(
                            currentCode,
                            change.newCode,
                            change.startLine,
                            change.endLine,
                            index + 1,
                            fixSuggestion.changes.size,
                            fixSuggestion.explanation,
                            fixSuggestion.suggestionId
                        )

                        getService(project, FixSuggestionInlayHolder::class.java).addInlaySnippet(fixSuggestion.suggestionId, index, FixSuggestionInlayPanel(
                            project,
                            fixSuggestionSnippet,
                            it,
                            psiFile,
                            rangeMarker
                        ))
                    } catch (e: IndexOutOfBoundsException) {
                        SonarLintConsole.get(project).error("Fix is invalid", e)
                        successfullyOpened = false
                    }
                }
            }

            if (firstTime) {
                if (!successfullyOpened) {
                    get(project).simpleNotification(
                        null,
                        "Unable to open the fix suggestion, your file has probably changed",
                        NotificationType.WARNING
                    )
                } else if (isBeforeContentIdentical(document, fixSuggestion.changes)) {
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
    }

    private fun isWithinBounds(document: Document, changes: List<FixChanges>): Boolean {
        return changes.all { change ->
            val lineStart = change.startLine
            val lineEnd = change.endLine

            return lineStart <= document.lineCount && lineEnd <= document.lineCount
        }
    }

    private fun isBeforeContentIdentical(document: Document, changes: List<FixChanges>): Boolean {
        return changes.all { change ->
            if (change.beforeCode == null) {
                return true
            }
            val lineStart = change.startLine
            val lineEnd = change.endLine

            val lineStartOffset = document.getLineStartOffset(lineStart - 1)
            val lineEndOffset = document.getLineEndOffset(lineEnd - 1)
            val documentBeforeCode = document.getText(TextRange(lineStartOffset, lineEndOffset))

            return documentBeforeCode.trim() == change.beforeCode.trim()
        }
    }

    private fun mapToLocalFixSuggestion(fixSuggestionDto: FixSuggestionDto): LocalFixSuggestion {
        val changes = fixSuggestionDto.fileEdit().changes().map { change ->
            FixChanges(
                startLine = change.beforeLineRange().startLine,
                endLine = change.beforeLineRange().endLine,
                beforeCode = change.before(),
                newCode = change.after()
            )
        }

        return LocalFixSuggestion(
            suggestionId = fixSuggestionDto.suggestionId(),
            explanation = fixSuggestionDto.explanation(),
            changes = changes
        )
    }

}
