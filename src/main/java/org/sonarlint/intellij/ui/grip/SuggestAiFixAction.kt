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

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import java.net.URI
import java.time.Instant
import org.sonarlint.intellij.actions.AbstractSonarAction
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.notifications.OpenGlobalSettingsAction
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.get
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.ProgressUtils.waitForFuture
import org.sonarlint.intellij.util.VirtualFileUtils
import org.sonarlint.intellij.util.computeInEDT

class SuggestAiFixAction(
    private val finding: Finding,
) : AbstractSonarAction("Suggest AI Fix") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        suggestAiFix(project, finding)
    }

    companion object {
        fun suggestAiFix(
            project: Project,
            finding: Finding,
        ) {
            val inlayHolder = getService(project, InlayHolder::class.java)
            val serviceUri = URI(getGlobalSettings().gripUrl)
            val serviceAuthToken = getGlobalSettings().gripAuthToken
            val promptVersion = getGlobalSettings().gripPromptVersion
            if (promptVersion.isNullOrEmpty()) {
                SonarLintConsole.get(project).error("GRIP prompt version is not set, please check your configuration")
                get(project).simpleNotification(
                    null,
                    "GRIP prompt version is not set, please check your configuration",
                    NotificationType.ERROR,
                    OpenGlobalSettingsAction(project)
                )
                return
            }
            if (serviceUri.toString().isEmpty() || serviceAuthToken.isNullOrEmpty()) {
                SonarLintConsole.get(project).error("GRIP URL or GRIP auth token are missing, please check your configuration")
                get(project).simpleNotification(
                    null, "GRIP URL or GRIP auth token are missing, please check your configuration", NotificationType.ERROR,
                    OpenGlobalSettingsAction(project)
                )
                return
            }

            val vFile = finding.file() ?: run {
                SonarLintConsole.get(project).error("File not found for finding: ${finding.getId()}")
                get(project).simpleNotification(
                    null, "File not found for finding: ${finding.getId()}", NotificationType.ERROR
                )
                return
            }

            val textRange = finding.getTextRange() ?: run {
                SonarLintConsole.get(project).error("Text range not found for finding: ${finding.getId()}")
                get(project).simpleNotification(
                    null, "Text range not found for finding: ${finding.getId()}", NotificationType.ERROR
                )
                return
            }

            val module = finding.module() ?: run {
                SonarLintConsole.get(project).error("Module not found for finding: ${finding.getId()}")
                get(project).simpleNotification(
                    null, "Module not found for finding: ${finding.getId()}", NotificationType.ERROR
                )
                return
            }

            val editor = ApplicationManager.getApplication().computeInEDT {
                val fileEditorManager = FileEditorManager.getInstance(project)
                fileEditorManager.openFile(vFile, true)
                fileEditorManager.selectedTextEditor
            }

            val inlayQuickFixPanel = ApplicationManager.getApplication().computeInEDT {
                if (editor != null) {
                    val inlay = InlayQuickFixPanel(project, editor, textRange.startLine, finding.getId(), vFile, null, 0, null)
                    val snippetData = inlayHolder.getInlayData(finding.getId())?.inlaySnippets ?: mutableListOf()
                    snippetData.add(InlaySnippetData(inlay, AiFindingState.INIT, 0, null))
                    inlayHolder.addInlayData(
                        finding.getId(),
                        InlayData(snippetData, AiFindingState.INIT, Instant.now(), null, false, finding.getMessage())
                    )
                    inlay
                } else {
                    null
                }
            }

            val fileUri = VirtualFileUtils.toURI(vFile)
            if (inlayQuickFixPanel != null && fileUri != null) {
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Suggesting a fix", false) {
                    override fun run(indicator: ProgressIndicator) {
                        val task = getService(BackendService::class.java).suggestFix(
                            serviceUri,
                            serviceAuthToken,
                            promptVersion,
                            module,
                            fileUri,
                            finding.getMessage(),
                            textRange,
                            finding.getRuleKey()
                        )

                        inlayHolder.getInlayData(finding.getId())?.inlaySnippets?.forEach { inlaySnippet ->
                            inlayHolder.updateStatusInlayPanel(
                                AiFindingState.LOADING, finding.getId(), inlaySnippet.inlayPanel
                            )
                        }
                        runOnUiThread(project) {
                            getService(
                                project, SonarLintToolWindow::class.java
                            ).tryShowAiLoading(finding)
                        }

                        try {
                            val result = waitForFuture(indicator, task).result

                            if (result.isRight) {
                                val success = result.right

                                runOnUiThread(project) {
                                    val psiManager = PsiManager.getInstance(project)
                                    val psiFile = psiManager.findFile(vFile)
                                    inlayHolder.removeAndDisposeInlayData(finding.getId())
                                    if (psiFile != null && editor != null && success.suggestedFix != null) {
                                        success.suggestedFix!!.diffs?.forEachIndexed { index, diffDto ->
                                            run {
                                                ApplicationManager.getApplication().computeInEDT {
                                                    val inlay = InlayQuickFixPanel(
                                                        project,
                                                        editor,
                                                        diffDto.beforeLineRangeInDocument.start,
                                                        finding.getId(),
                                                        vFile,
                                                        success.correlationId,
                                                        index,
                                                        success.suggestedFix!!.diffs.size
                                                    )

                                                    val snippetData =
                                                        inlayHolder.getInlayData(finding.getId())?.inlaySnippets ?: mutableListOf()
                                                    snippetData.add(InlaySnippetData(inlay, AiFindingState.INIT, 0, null))
                                                    inlayHolder.addInlayData(
                                                        finding.getId(),
                                                        InlayData(
                                                            snippetData,
                                                            AiFindingState.INIT,
                                                            Instant.now(),
                                                            success.correlationId,
                                                            false,
                                                            finding.getMessage()
                                                        )
                                                    )

                                                    inlay.updatePanelWithData(
                                                        psiFile,
                                                        diffDto.after,
                                                        diffDto.beforeLineRangeInDocument.start,
                                                        diffDto.beforeLineRangeInDocument.end
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    inlayHolder.getInlayData(finding.getId())?.inlaySnippets?.forEach { inlaySnippet ->
                                        inlayHolder.updateStatusInlayPanel(
                                            AiFindingState.LOADED, finding.getId(), inlaySnippet.inlayPanel
                                        )
                                    }

                                    var aiResponse = """
                                        # Explanation
                                        
                                        ${success.suggestedFix.explanation}
                                        """.trimIndent()

                                    success.suggestedFix!!.diffs?.forEachIndexed { index, diffDto ->
                                        aiResponse += "\n${System.lineSeparator()}" +
                                            "## Location ${index + 1}\n" +
                                            "### Before:" +
                                            "```" +
                                            diffDto.before +
                                            "```" +
                                            "### After:" +
                                            "```" +
                                            diffDto.after +
                                            "```"
                                    }

                                    getService(project, SonarLintToolWindow::class.java).tryShowAiResponse(aiResponse, finding)
                                }
                            } else {
                                val error = result.left

                                inlayHolder.getInlayData(finding.getId())?.inlaySnippets?.forEach { inlaySnippet ->
                                    inlayHolder.updateStatusInlayPanel(
                                        AiFindingState.FAILED, finding.getId(), inlaySnippet.inlayPanel
                                    )
                                }
                                runOnUiThread(project) {
                                    getService(project, SonarLintToolWindow::class.java).tryShowAiResponseFailure(
                                        error.message, finding
                                    )
                                }
                                inlayQuickFixPanel.dispose()
                            }
                        } catch (e: ProcessCanceledException) {
                            inlayHolder.getInlayData(finding.getId())?.inlaySnippets?.forEach { inlaySnippet ->
                                inlayHolder.updateStatusInlayPanel(
                                    AiFindingState.FAILED, finding.getId(), inlaySnippet.inlayPanel
                                )
                            }
                            e.message?.let { SonarLintConsole.get(project).debug(it) }
                            runOnUiThread(project) {
                                getService(project, SonarLintToolWindow::class.java).tryShowAiResponseFailure(
                                    "Computation was cancelled", finding
                                )
                            }
                            inlayQuickFixPanel.dispose()
                        } catch (e: Exception) {
                            inlayHolder.getInlayData(finding.getId())?.inlaySnippets?.forEach { inlaySnippet ->
                                inlayHolder.updateStatusInlayPanel(
                                    AiFindingState.FAILED, finding.getId(), inlaySnippet.inlayPanel
                                )
                            }
                            SonarLintConsole.get(project).error("Fix issue", e)
                            runOnUiThread(project) {
                                getService(
                                    project, SonarLintToolWindow::class.java
                                ).tryShowAiResponseFailure("Error while computing the suggestion: ${e.message}", finding)
                            }
                            inlayQuickFixPanel.dispose()
                        }
                    }
                })
            }
        }
    }

}
