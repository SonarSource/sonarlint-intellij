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
import org.jetbrains.annotations.VisibleForTesting
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.editor.EditorHighlightRefresh
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
        updateViewsWithNewFindings(analysisResult.findings, refreshHighlights = true, analyzedFiles = analysisResult.analyzedFiles)

    fun updateOnAnalysisIntermediateResult(intermediateResult: AnalysisIntermediateResult) =
        updateViewsWithNewFindings(intermediateResult.findings, refreshHighlights = false, analyzedFiles = null)

    private fun updateViewsWithNewFindings(
        findings: LiveFindings,
        refreshHighlights: Boolean,
        analyzedFiles: Collection<VirtualFile>?,
    ) {
        ensureSelectedFileIsSet()
        // Temporary workaround as FileEditorManager.openFiles does not return open files on dev containers/SSH
        val openedFiles = openFiles.ifEmpty { setOfNotNull(selectedFile) }
        val filteredFindings = findings.onlyFor(openedFiles)
        val previouslyHighlightedOpenFiles = (currentIssuesPerOpenFile.keys + currentSecurityHotspotsPerOpenFile.keys)
            .intersect(openedFiles)
        filteredFindings.issuesPerFile.forEach { (file, issues) -> currentIssuesPerOpenFile[file] = issues }
        filteredFindings.securityHotspotsPerFile.forEach { (file, hotspots) ->
            currentSecurityHotspotsPerOpenFile[file] = hotspots
        }
        val filesWithEmptyReplacements = if (analyzedFiles != null) {
            val analyzedOpenFiles = analyzedFiles.filter { it in openedFiles }.toSet()
            analyzedOpenFiles.forEach { file ->
                if (file !in filteredFindings.issuesPerFile) {
                    currentIssuesPerOpenFile[file] = emptyList()
                }
                if (file !in filteredFindings.securityHotspotsPerFile) {
                    currentSecurityHotspotsPerOpenFile[file] = emptyList()
                }
            }
            analyzedOpenFiles
        } else {
            emptySet()
        }
        val changedFiles = filesWithEmptyReplacements + previouslyHighlightedOpenFiles + filteredFindings.filesInvolved
        publishViewUpdate(
            highlightRefresh = if (refreshHighlights) EditorHighlightRefresh.enabled(changedFiles) else EditorHighlightRefresh.NONE,
            // Security hotspots live in their own tab, so a full refresh is required to keep it in sync.
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

        issues.forEach { (file, liveIssues) -> currentIssuesPerOpenFile[file] = liveIssues }
        ensureSelectedFileIsSet()
        publishViewUpdate(if (isIntermediate) EditorHighlightRefresh.NONE else EditorHighlightRefresh.enabled(issues.keys))
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

        securityHotspots.forEach { (file, liveHotspots) -> currentSecurityHotspotsPerOpenFile[file] = liveHotspots }
        ensureSelectedFileIsSet()
        publishViewUpdate(
            highlightRefresh = if (isIntermediate) EditorHighlightRefresh.NONE else EditorHighlightRefresh.enabled(securityHotspots.keys),
            forceFullPanelRefresh = true,
        )
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        selectedFile = event.newFile
        // Re-highlight the newly selected file: its findings may already be known but not yet drawn in this editor.
        updateCurrentFileTab(EditorHighlightRefresh.enabled(listOfNotNull(selectedFile)))
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        currentIssuesPerOpenFile.remove(file)
        currentSecurityHotspotsPerOpenFile.remove(file)
        // The closed editor no longer needs highlighting and other editors are unaffected, so never refresh highlights.
        if (currentIssuesPerOpenFile.isEmpty()) {
            updateCurrentFileTab()
        } else {
            refreshViews(EditorHighlightRefresh.NONE)
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

    @VisibleForTesting
    fun replaceIssuesForFile(file: VirtualFile, issues: Collection<LiveIssue>) {
        currentIssuesPerOpenFile[file] = issues
    }

    fun clearAllCurrentFileFindings() {
        currentIssuesPerOpenFile.clear()
        currentSecurityHotspotsPerOpenFile.clear()
        // Findings are gone: refresh every open editor so their now-stale highlights are removed.
        updateCurrentFileTab(EditorHighlightRefresh.ALL_OPEN_FILES)
    }

    private fun ensureSelectedFileIsSet() {
        if (selectedFile == null) {
            runOnUiThread(project) {
                selectedFile = SonarLintUtils.getSelectedFile(project)
            }
        }
    }

    /**
     * Pushes the current findings to the tool window. When [forceFullPanelRefresh] is set, all tabs (including the
     * Security Hotspots tab) are rebuilt; otherwise only the Current File tab is updated. [highlightRefresh] is applied
     * by the coordinator before any tool-window update, so markup does not wait on the Current File tab.
     */
    private fun publishViewUpdate(highlightRefresh: EditorHighlightRefresh, forceFullPanelRefresh: Boolean = false) {
        if (project.isDisposed) {
            return
        }
        getService(project, OnTheFlyFindingsCoordinator::class.java).applyHighlightRefresh(highlightRefresh)
        val toolWindow = getService(project, SonarLintToolWindow::class.java)
        if (forceFullPanelRefresh) {
            toolWindow.refreshViews(EditorHighlightRefresh.NONE)
        } else {
            toolWindow.updateCurrentFileTab(selectedFile)
        }
    }

    private fun refreshViews(highlightRefresh: EditorHighlightRefresh) {
        publishViewUpdate(highlightRefresh, forceFullPanelRefresh = true)
    }

    private fun updateCurrentFileTab(highlightRefresh: EditorHighlightRefresh = EditorHighlightRefresh.NONE) {
        publishViewUpdate(highlightRefresh)
    }

    private val openFiles: Set<VirtualFile>
        get() = FileEditorManager.getInstance(project).openFiles.toSet()
}
