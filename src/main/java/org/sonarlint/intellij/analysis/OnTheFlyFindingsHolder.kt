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
import org.sonarlint.intellij.finding.LiveFinding
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
        updateViewsWithNewFindings(analysisResult.findings)

    fun updateOnAnalysisIntermediateResult(intermediateResult: AnalysisIntermediateResult) =
        updateViewsWithNewFindings(intermediateResult.findings)

    private fun updateViewsWithNewFindings(findings: LiveFindings) {
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
        updateSecurityHotspots()
        getService(project, CodeAnalyzerRestarter::class.java).refreshFiles(findings.onlyFor(openedFiles).filesInvolved)
    }

    fun updateViewsWithNewIssues(module: Module, raisedIssues: Map<URI, List<RaisedIssueDto>>) {
        val issues = raisedIssues.mapNotNull { (uri, rawIssues) ->
            val virtualFile = uriToVirtualFile(uri) ?: return
            val liveIssues = rawIssues.mapNotNull {
                RawIssueAdapter.toLiveIssue(module, it, virtualFile, null)
            }
            virtualFile to liveIssues
        }.toMap()
        currentIssuesPerOpenFile.putAll(issues)
        if (selectedFile == null) {
            runOnUiThread(project) {
                selectedFile = SonarLintUtils.getSelectedFile(project)
            }
        }
        updateCurrentFileTab()
        getService(project, CodeAnalyzerRestarter::class.java).refreshFiles(issues.keys)
    }

    fun updateViewsWithNewSecurityHotspots(module: Module, raisedSecurityHotspots: Map<URI, List<RaisedHotspotDto>>) {
        val securityHotspots = raisedSecurityHotspots.mapNotNull { (uri, rawSecurityHotspots) ->
            val virtualFile = uriToVirtualFile(uri) ?: return
            val liveIssues = rawSecurityHotspots.mapNotNull {
                RawIssueAdapter.toLiveSecurityHotspot(module, it, virtualFile, null)
            }
            virtualFile to liveIssues
        }.toMap().filterKeys { it in openFiles }
        currentSecurityHotspotsPerOpenFile.putAll(securityHotspots)
        if (selectedFile == null) {
            runOnUiThread(project) {
                selectedFile = SonarLintUtils.getSelectedFile(project)
            }
        }
        updateSecurityHotspots()
        getService(project, CodeAnalyzerRestarter::class.java).refreshFiles(securityHotspots.keys)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile
        selectedFile = file
        updateCurrentFileTab()
        updateTaintTab()
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        currentIssuesPerOpenFile.remove(file)
        currentSecurityHotspotsPerOpenFile.remove(file)
        // update only Security Hotspots, issues will be updated in reaction to selectionChanged
        updateSecurityHotspots()
        if (currentIssuesPerOpenFile.isEmpty()) {
            updateCurrentFileTab()
        }

        runOnPooledThread(project) {
            findModuleForFile(file, project)?.let {
                getService(BackendService::class.java).didCloseFile(it, file)
            } ?: run {
                getService(BackendService::class.java).didCloseFile(project, file)
            }
        }
    }

    private fun updateSecurityHotspots() {
        if (!project.isDisposed) {
            getService(
                project, SonarLintToolWindow::class.java
            ).updateOnTheFlySecurityHotspots(currentSecurityHotspotsPerOpenFile)
        }
    }

    private fun updateCurrentFileTab() {
        if (!project.isDisposed) {
            getService(
                project, SonarLintToolWindow::class.java
            ).updateCurrentFileTab(selectedFile)
        }
    }

    private fun updateTaintTab() {
        if (!project.isDisposed) {
            getService(
                project, SonarLintToolWindow::class.java
            ).refreshTaintCodeFix()
        }
    }

    fun getFindingsForFile(file: VirtualFile): Collection<LiveFinding> {
        return currentIssuesPerOpenFile[file]?.plus(currentSecurityHotspotsPerOpenFile[file] ?: emptyList()) ?: emptyList()
    }

    fun getIssuesForFile(file: VirtualFile): Collection<LiveIssue> {
        return currentIssuesPerOpenFile[file] ?: emptyList()
    }

    fun getSecurityHotspotsForFile(file: VirtualFile): Collection<LiveSecurityHotspot> {
        return currentSecurityHotspotsPerOpenFile[file] ?: emptyList()
    }

    fun clearCurrentFile() {
        if (selectedFile == null) {
            selectedFile = SonarLintUtils.getSelectedFile(project)
        }
        if (selectedFile != null) {
            currentIssuesPerOpenFile.remove(selectedFile)
        }
        updateCurrentFileTab()
    }

    private val openFiles: Set<VirtualFile>
        get() = FileEditorManager.getInstance(project).openFiles.toSet()
}
