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
import com.intellij.openapi.vfs.VirtualFile
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.IssueToFixDto

class SuggestAiFixesAction(
    private val file: VirtualFile,
    private val findings: List<Finding>,
) : AbstractSonarAction("Suggest AI Fixes") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        suggestAiFixes(project, file, findings)
    }

    companion object {
        fun suggestAiFixes(
            project: Project,
            file: VirtualFile,
            findings: List<Finding>,
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

            val module = findings.first().module() ?: run {
                SonarLintConsole.get(project).error("Module not found for finding: ${findings.first().getId()}")
                get(project).simpleNotification(
                    null, "Module not found for finding: ${findings.first().getId()}", NotificationType.ERROR
                )
                return
            }

            val editor = ApplicationManager.getApplication().computeInEDT {
                val fileEditorManager = FileEditorManager.getInstance(project)
                fileEditorManager.openFile(file, true)
                fileEditorManager.selectedTextEditor
            }

            findings.forEach {
                inlayHolder.addInlayData(
                    it.getId(),
                    InlayData(mutableListOf(), AiFindingState.LOADING, Instant.now(), null, false, it.getMessage())
                )
            }

            val listIssuesDto =
                findings.mapNotNull { it.getTextRange()?.let { it1 -> IssueToFixDto(it.getMessage(), it1, it.getRuleKey()) } }

            val fileUri = VirtualFileUtils.toURI(file)
            if (fileUri != null) {
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Suggesting a fix", false) {
                    override fun run(indicator: ProgressIndicator) {
                        val task = getService(BackendService::class.java).suggestFixes(
                            serviceUri,
                            serviceAuthToken,
                            promptVersion,
                            module,
                            fileUri,
                            listIssuesDto
                        )

                        runOnUiThread(project) {
                            getService(
                                project, SonarLintToolWindow::class.java
                            ).tryShowAiLoading(findings)
                        }

                        try {
                            val results = waitForFuture(indicator, task).results

                            results.forEachIndexed { index, result ->
                                if (result.suggestions.isRight) {
                                    val success = result.suggestions.right

                                    runOnUiThread(project) {
                                        val psiManager = PsiManager.getInstance(project)
                                        val psiFile = psiManager.findFile(file)
                                        if (psiFile != null && editor != null && success.suggestedFix != null) {
                                            success.suggestedFix!!.diffs?.forEachIndexed { indexDiff, diffDto ->
                                                run {
                                                    ApplicationManager.getApplication().computeInEDT {
                                                        val inlay = InlayQuickFixPanel(
                                                            project,
                                                            editor,
                                                            diffDto.beforeLineRangeInDocument.start,
                                                            findings[index].getId(),
                                                            file,
                                                            success.correlationId,
                                                            indexDiff,
                                                            success.suggestedFix!!.diffs.size,
                                                            findings[index].getRuleKey()
                                                        )

                                                        val snippetData =
                                                            inlayHolder.getInlayData(findings[index].getId())?.inlaySnippets ?: mutableListOf()
                                                        snippetData.add(InlaySnippetData(inlay, AiFindingState.INIT, 0, null))
                                                        inlayHolder.addInlayData(
                                                            findings[index].getId(),
                                                            InlayData(
                                                                snippetData,
                                                                AiFindingState.INIT,
                                                                Instant.now(),
                                                                success.correlationId,
                                                                false,
                                                                findings[index].getMessage()
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
                                        inlayHolder.getInlayData(findings[index].getId())?.inlaySnippets?.forEach { inlaySnippet ->
                                            inlayHolder.updateStatusInlayPanel(
                                                AiFindingState.LOADED, findings[index].getId(), inlaySnippet.inlayPanel
                                            )
                                        }

                                        var aiResponse = """
                                        # Explanation
                                        
                                        ${success.suggestedFix.explanation}
                                        """.trimIndent()

                                        success.suggestedFix!!.diffs?.forEachIndexed { indexDiff, diffDto ->
                                            aiResponse += "\n${System.lineSeparator()}" +
                                                "## Location ${indexDiff + 1}\n" +
                                                "### Before:" +
                                                "```" +
                                                diffDto.before +
                                                "```" +
                                                "### After:" +
                                                "```" +
                                                diffDto.after +
                                                "```"
                                        }

                                        getService(project, SonarLintToolWindow::class.java).tryShowAiResponse(aiResponse, findings[index])
                                    }
                                } else {
                                    val error = result.suggestions.left
                                    inlayHolder.updateAllFixesWhenFailureAndNoPanel(findings[index].getId())
                                    runOnUiThread(project) {
                                        getService(project, SonarLintToolWindow::class.java).tryShowAiResponseFailure(
                                            error.message, findings[index]
                                        )
                                    }
                                }
                            }
                        } catch (e: ProcessCanceledException) {
                            findings.forEach {
                                inlayHolder.updateAllFixesWhenFailureAndNoPanel(it.getId())
                            }
                            e.message?.let { SonarLintConsole.get(project).debug(it) }
                            runOnUiThread(project) {
                                getService(project, SonarLintToolWindow::class.java).tryShowAiResponseFailure(
                                    "Computation was cancelled", findings.first()
                                )
                            }
                        } catch (e: Exception) {
                            findings.forEach {
                                inlayHolder.updateAllFixesWhenFailureAndNoPanel(it.getId())
                            }
                            SonarLintConsole.get(project).error("Fix issue", e)
                            runOnUiThread(project) {
                                getService(
                                    project, SonarLintToolWindow::class.java
                                ).tryShowAiResponseFailure("Error while computing the suggestion: ${e.message}", findings.first())
                            }
                        }
                    }
                })
            }
        }
    }

}
