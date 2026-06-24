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
package org.sonarlint.intellij.analysis

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.LiveFindings
import org.sonarlint.intellij.finding.RawIssueAdapter
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.VirtualFileUtils.uriToVirtualFile
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.RaisedHotspotDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto

class OnTheFlyFindingsHolder(private val project: Project) : FileEditorManagerListener {
    private var selectedFile: VirtualFile? = null
    private val currentIssuesPerOpenFile: MutableMap<VirtualFile, Collection<LiveIssue>> = ConcurrentHashMap()
    private val currentSecurityHotspotsPerOpenFile: MutableMap<VirtualFile, Collection<LiveSecurityHotspot>> = ConcurrentHashMap()

    init {
        project.messageBus.connect()
            .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
    }

    fun updateOnAnalysisResult(analysisResult: AnalysisResult) =
        updateViewsWithNewFindings(analysisResult.findings, refreshHighlights = true)

    fun updateOnAnalysisIntermediateResult(intermediateResult: AnalysisIntermediateResult) =
        updateViewsWithNewFindings(intermediateResult.findings, refreshHighlights = false)

    private fun updateViewsWithNewFindings(findings: LiveFindings, refreshHighlights: Boolean) {
        ensureSelectedFileIsSet()
        // Temporary workaround as FileEditorManager.openFiles does not return open files on dev containers/SSH
        val openedFiles = openFiles.ifEmpty { setOfNotNull(selectedFile) }
        val filteredFindings = findings.onlyFor(openedFiles)
        with(filteredFindings) {
            currentIssuesPerOpenFile.putAll(issuesPerFile)
            currentSecurityHotspotsPerOpenFile.putAll(securityHotspotsPerFile)
        }
        publishViewUpdate(
            refreshHighlights = refreshHighlights,
            changedFiles = filteredFindings.filesInvolved.intersect(openedFiles),
            forceFullPanelRefresh = filteredFindings.securityHotspotsPerFile.isNotEmpty(),
        )
    }

    fun updateViewsWithNewIssues(module: Module, raisedIssues: Map<URI, List<RaisedIssueDto>>, isIntermediate: Boolean = false) {
        val issues = raisedIssues.mapNotNull { (uri, rawIssues) ->
            val virtualFile = uriToVirtualFile(uri) ?: return@mapNotNull null
            if (virtualFile in openFiles || virtualFile == selectedFile) {
                val liveIssues = rawIssues.mapNotNull {
                    RawIssueAdapter.toLiveIssue(module, it, virtualFile, null)
                }
                virtualFile to liveIssues
            } else {
                null
            }
        }.toMap()

        currentIssuesPerOpenFile.putAll(issues)
        ensureSelectedFileIsSet()
        publishViewUpdate(refreshHighlights = !isIntermediate, changedFiles = issues.keys)
    }

    fun updateViewsWithNewSecurityHotspots(module: Module, raisedSecurityHotspots: Map<URI, List<RaisedHotspotDto>>, isIntermediate: Boolean = false) {
        val securityHotspots = raisedSecurityHotspots.mapNotNull { (uri, rawSecurityHotspots) ->
            val virtualFile = uriToVirtualFile(uri) ?: return@mapNotNull null
            if (virtualFile in openFiles || virtualFile == selectedFile) {
                val liveHotspots = rawSecurityHotspots.mapNotNull {
                    RawIssueAdapter.toLiveSecurityHotspot(module, it, virtualFile, null)
                }
                virtualFile to liveHotspots
            } else {
                null
            }
        }.toMap()

        currentSecurityHotspotsPerOpenFile.putAll(securityHotspots)
        ensureSelectedFileIsSet()
        publishViewUpdate(
            refreshHighlights = !isIntermediate,
            changedFiles = securityHotspots.keys,
            forceFullPanelRefresh = true,
        )
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        selectedFile = event.newFile
        updateCurrentFileTab(refreshEditorHighlights = true)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        currentIssuesPerOpenFile.remove(file)
        currentSecurityHotspotsPerOpenFile.remove(file)
        if (currentIssuesPerOpenFile.isEmpty()) {
            updateCurrentFileTab()
        } else {
            refreshViews(refreshEditorHighlights = false)
        }

        runOnPooledThread(project) {
            findModuleForFile(file, project)?.let {
                getService(BackendService::class.java).didCloseFile(it, file)
            } ?: run {
                getService(BackendService::class.java).didCloseFile(project, file)
            }
        }
    }

    fun getAllIssues(): Collection<LiveIssue> {
        return currentIssuesPerOpenFile.values.flatten()
    }

    fun getAllHotspots(): Collection<LiveSecurityHotspot> {
        return currentSecurityHotspotsPerOpenFile.values.flatten()
    }

    fun getIssuesForFile(file: VirtualFile): Collection<LiveIssue> {
        return currentIssuesPerOpenFile[file] ?: emptyList()
    }

    fun getSecurityHotspotsForFile(file: VirtualFile): Collection<LiveSecurityHotspot> {
        return currentSecurityHotspotsPerOpenFile[file] ?: emptyList()
    }

    fun clearAllCurrentFileFindings() {
        currentIssuesPerOpenFile.clear()
        currentSecurityHotspotsPerOpenFile.clear()
        updateCurrentFileTab(refreshEditorHighlights = true, highlightAllOpenFiles = true)
    }

    private fun ensureSelectedFileIsSet() {
        if (selectedFile == null) {
            runOnUiThread(project) {
                selectedFile = SonarLintUtils.getSelectedFile(project)
            }
        }
    }

    private fun publishViewUpdate(
        refreshHighlights: Boolean,
        changedFiles: Set<VirtualFile> = emptySet(),
        forceFullPanelRefresh: Boolean = false,
    ) {
        if (project.isDisposed) {
            return
        }
        val toolWindow = getService(project, SonarLintToolWindow::class.java)
        val changedFilesList = changedFiles.takeIf { it.isNotEmpty() }?.toList()
        if (forceFullPanelRefresh) {
            toolWindow.refreshViews(refreshHighlights, changedFilesList, false)
        } else {
            toolWindow.updateCurrentFileTab(selectedFile, refreshHighlights, changedFilesList, false)
        }
    }

    private fun refreshViews(refreshEditorHighlights: Boolean) {
        if (!project.isDisposed) {
            getService(project, SonarLintToolWindow::class.java).refreshViews(refreshEditorHighlights)
        }
    }

    private fun updateCurrentFileTab(
        refreshEditorHighlights: Boolean = false,
        highlightChangedFiles: Collection<VirtualFile>? = null,
        highlightAllOpenFiles: Boolean = false,
    ) {
        if (!project.isDisposed) {
            getService(project, SonarLintToolWindow::class.java).updateCurrentFileTab(
                selectedFile,
                refreshEditorHighlights,
                highlightChangedFiles,
                highlightAllOpenFiles,
            )
        }
    }

    private val openFiles: Set<VirtualFile>
        get() = FileEditorManager.getInstance(project).openFiles.toSet()
}
