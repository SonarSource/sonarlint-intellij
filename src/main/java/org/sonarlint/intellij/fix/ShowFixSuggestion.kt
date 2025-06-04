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
import org.sonarlint.intellij.ui.inlay.FixSuggestionInlayHolder
import org.sonarlint.intellij.ui.inlay.FixSuggestionInlayPanel
import org.sonarlint.intellij.ui.inlay.InlayManager
import org.sonarlint.intellij.util.getDocument
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixResponse
import org.sonarsource.sonarlint.core.rpc.protocol.client.fix.FixSuggestionDto

data class LocalFixChange(
    val startLine: Int,
    val endLine: Int,
    val before: String,
    val after: String
)

data class LocalFixSuggestion(
    val id: String,
    val explanation: String,
    val changes: List<LocalFixChange>
)

class ShowFixSuggestion(private val project: Project, private val file: VirtualFile) {

    companion object {
        private const val FILE_CHANGED_ERROR = "Unable to open the fix suggestion, your file has probably changed. Please verify you are in the right branch."
        private const val FILE_CHANGED_SUCCESS =
            "The fix suggestion has been opened, but the file's content has changed, so it may not be applicable. Please verify you are in the right branch."
        private const val SUCCESSFULLY_OPENED = "The fix suggestion has been successfully opened"
    }

    fun show(fixSuggestion: FixSuggestionDto) {
        val localFixSuggestion = mapToLocalFixSuggestion(fixSuggestion)
        show(localFixSuggestion, false)
    }

    /**
     * @param alreadySuggested If the fix was already shown and was closed (not resolved) we display it again if it's still in memory
     */
    fun show(fixSuggestion: SuggestFixResponse, alreadySuggested: Boolean) {
        val localFixSuggestion = mapToLocalFixSuggestion(fixSuggestion) ?: let {
            get(project).simpleNotification(null, FILE_CHANGED_ERROR, NotificationType.WARNING)
            return
        }
        show(localFixSuggestion, alreadySuggested)
    }

    fun show(fixSuggestion: LocalFixSuggestion, alreadySuggested: Boolean) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val psiFile = computeReadActionSafely(project) { PsiManager.getInstance(project).findFile(file) } ?: return
        val document = computeReadActionSafely(project) { file.getDocument() } ?: return

        if (!isWithinBounds(document, fixSuggestion.changes)) {
            get(project).simpleNotification(
                null, FILE_CHANGED_ERROR, NotificationType.WARNING
            )
            return
        }

        var successfullyOpened = true

        runOnUiThread(project, ModalityState.defaultModalityState()) {
            fixSuggestion.changes.forEachIndexed { index, change ->
                if (alreadySuggested && !getService(project, FixSuggestionInlayHolder::class.java).shouldShowSnippet(
                        fixSuggestion.id,
                        index
                    )
                ) return@forEachIndexed

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
                        val rangeMarker =
                            doc.createRangeMarker(doc.getLineStartOffset(change.startLine - 1), doc.getLineEndOffset(change.endLine - 1))
                        val currentCode = doc.getText(TextRange(rangeMarker.startOffset, rangeMarker.endOffset))
                        val fixSuggestionSnippet = FixSuggestionSnippet(
                            currentCode,
                            change.after,
                            index + 1,
                            fixSuggestion.changes.size,
                            fixSuggestion.explanation,
                            fixSuggestion.id
                        )

                        val inlayPanel = FixSuggestionInlayPanel(
                            project,
                            fixSuggestionSnippet,
                            it,
                            psiFile,
                            rangeMarker
                        )

                        getService(project, FixSuggestionInlayHolder::class.java).addInlaySnippet(fixSuggestion.id, index, inlayPanel)
                    } catch (e: IndexOutOfBoundsException) {
                        SonarLintConsole.get(project).error("Fix is invalid", e)
                        successfullyOpened = false
                    }
                }
            }

            if (!alreadySuggested) {
                handleNotifications(successfullyOpened, document, fixSuggestion)
            }
        }
    }

    private fun handleNotifications(successfullyOpened: Boolean, document: Document, fixSuggestion: LocalFixSuggestion) {
        if (!successfullyOpened) {
            get(project).simpleNotification(
                null,
                FILE_CHANGED_ERROR,
                NotificationType.WARNING
            )
        } else if (isBeforeContentIdentical(document, fixSuggestion.changes)) {
            get(project).simpleNotification(
                null,
                SUCCESSFULLY_OPENED,
                NotificationType.INFORMATION
            )
        } else {
            get(project).simpleNotification(
                null,
                FILE_CHANGED_SUCCESS,
                NotificationType.WARNING
            )
        }
    }

    private fun isWithinBounds(document: Document, changes: List<LocalFixChange>): Boolean {
        return changes.all { change ->
            val lineStart = change.startLine
            val lineEnd = change.endLine

            return lineStart <= document.lineCount && lineEnd <= document.lineCount
        }
    }

    private fun isBeforeContentIdentical(document: Document, changes: List<LocalFixChange>): Boolean {
        return changes.all { change ->

            val lineStart = change.startLine
            val lineEnd = change.endLine

            val lineStartOffset = document.getLineStartOffset(lineStart - 1)
            val lineEndOffset = document.getLineEndOffset(lineEnd - 1)
            val documentBeforeCode = document.getText(TextRange(lineStartOffset, lineEndOffset))

            return documentBeforeCode.trim() == change.before.trim()
        }
    }

    private fun mapToLocalFixSuggestion(fixResponse: SuggestFixResponse): LocalFixSuggestion? {
        val document = computeReadActionSafely(project) { file.getDocument() } ?: return null

        val changes = fixResponse.changes.map { change ->
            val lineStartOffset = document.getLineStartOffset(change.startLine - 1)
            val lineEndOffset = document.getLineEndOffset(change.endLine - 1)
            val documentBeforeCode = document.getText(TextRange(lineStartOffset, lineEndOffset))

            LocalFixChange(
                change.startLine,
                change.endLine,
                documentBeforeCode,
                change.newCode
            )
        }

        return LocalFixSuggestion(
            fixResponse.id.toString(),
            fixResponse.explanation,
            changes
        )
    }

    private fun mapToLocalFixSuggestion(fixSuggestionDto: FixSuggestionDto): LocalFixSuggestion {
        val changes = fixSuggestionDto.fileEdit().changes().map { change ->
            LocalFixChange(
                change.beforeLineRange().startLine,
                change.beforeLineRange().endLine,
                change.before(),
                change.after()
            )
        }

        return LocalFixSuggestion(
            fixSuggestionDto.suggestionId(),
            fixSuggestionDto.explanation(),
            changes
        )
    }

}
