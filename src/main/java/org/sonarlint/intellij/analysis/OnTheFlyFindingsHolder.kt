/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
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

    fun updateOnAnalysisResult(analysisResult: AnalysisResult) {
        val files = updateViewsWithNewFindings(analysisResult.findings)
        getService(project, CodeAnalyzerRestarter::class.java).refreshFiles(files)
    }

    fun updateOnAnalysisIntermediateResult(intermediateResult: AnalysisIntermediateResult) {
        updateViewsWithNewFindings(intermediateResult.findings)
    }

    private fun updateViewsWithNewFindings(findings: LiveFindings): Set<VirtualFile> {
        if (selectedFile == null) {
            runOnUiThread(project) {
                selectedFile = SonarLintUtils.getSelectedFile(project)
            }
        }
        // Temporary workaround as FileEditorManager.openFiles does not return open files on dev containers/SSH
        val openedFiles = openFiles.ifEmpty { setOfNotNull(selectedFile) }
        with(findings.onlyFor(openedFiles)) {
            currentIssuesPerOpenFile.putAll(issuesPerFile)
            currentSecurityHotspotsPerOpenFile.putAll(securityHotspotsPerFile)
        }
        updateCurrentFileTab()
        return openedFiles
    }

    fun updateViewsWithNewIssues(module: Module, raisedIssues: Map<URI, List<RaisedIssueDto>>, isIntermediate: Boolean) {
        val issues = raisedIssues.mapNotNull { (uri, rawIssues) ->
            val virtualFile = uriToVirtualFile(uri) ?: return@mapNotNull null
            // Only include issues for files that are still open
            if (virtualFile in openFiles || virtualFile == selectedFile) {
                val liveIssues = rawIssues.mapNotNull {
                    RawIssueAdapter.toLiveIssue(module, it, virtualFile, null)
                }
                virtualFile to liveIssues
            } else {
                null // Skip findings for closed files
            }
        }.toMap()
        
        currentIssuesPerOpenFile.putAll(issues)
        if (selectedFile == null) {
            runOnUiThread(project) {
                selectedFile = SonarLintUtils.getSelectedFile(project)
            }
        }
        updateCurrentFileTab()
        if (!isIntermediate) {
            getService(project, CodeAnalyzerRestarter::class.java).refreshFiles(issues.keys)
        }
    }

    fun updateViewsWithNewSecurityHotspots(module: Module, raisedSecurityHotspots: Map<URI, List<RaisedHotspotDto>>) {
        val securityHotspots = raisedSecurityHotspots.mapNotNull { (uri, rawSecurityHotspots) ->
            val virtualFile = uriToVirtualFile(uri) ?: return@mapNotNull null
            // Only include hotspots for files that are still open
            if (virtualFile in openFiles || virtualFile == selectedFile) {
                val liveHotspots = rawSecurityHotspots.mapNotNull {
                    RawIssueAdapter.toLiveSecurityHotspot(module, it, virtualFile, null)
                }
                virtualFile to liveHotspots
            } else {
                null // Skip findings for closed files
            }
        }.toMap()
        
        currentSecurityHotspotsPerOpenFile.putAll(securityHotspots)
        if (selectedFile == null) {
            runOnUiThread(project) {
                selectedFile = SonarLintUtils.getSelectedFile(project)
            }
        }
        refreshViews()
        getService(project, CodeAnalyzerRestarter::class.java).refreshFiles(securityHotspots.keys)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile
        selectedFile = file
        updateCurrentFileTab()
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        currentIssuesPerOpenFile.remove(file)
        currentSecurityHotspotsPerOpenFile.remove(file)
        if (currentIssuesPerOpenFile.isEmpty()) {
            updateCurrentFileTab()
        } else {
            refreshViews()
        }

        runOnPooledThread(project) {
            findModuleForFile(file, project)?.let {
                getService(BackendService::class.java).didCloseFile(it, file)
            } ?: run {
                getService(BackendService::class.java).didCloseFile(project, file)
            }
        }
    }

    private fun refreshViews() {
        if (!project.isDisposed) {
            getService(project, SonarLintToolWindow::class.java).refreshViews()
        }
    }

    private fun updateCurrentFileTab() {
        if (!project.isDisposed) {
            getService(project, SonarLintToolWindow::class.java).updateCurrentFileTab(selectedFile)
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
        updateCurrentFileTab()
    }

    private val openFiles: Set<VirtualFile>
        get() = FileEditorManager.getInstance(project).openFiles.toSet()
}
