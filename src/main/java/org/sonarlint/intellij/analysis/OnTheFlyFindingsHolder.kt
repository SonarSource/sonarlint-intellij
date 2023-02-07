/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.finding.LiveFindings
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import java.util.concurrent.ConcurrentHashMap

class OnTheFlyFindingsHolder(private val project: Project) : FileEditorManagerListener {
    private var selectedFile: VirtualFile? = null
    private val currentIssuesPerOpenFile: MutableMap<VirtualFile, Collection<LiveIssue>> = ConcurrentHashMap()
    private val currentSecurityHotspotsPerOpenFile: MutableMap<VirtualFile, Collection<LiveSecurityHotspot>> = ConcurrentHashMap()

    init {
        ApplicationManager.getApplication().invokeAndWait { selectedFile = SonarLintUtils.getSelectedFile(project) }
        project.messageBus.connect()
            .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
    }

    fun updateOnAnalysisResult(analysisResult: AnalysisResult) =
        updateViewsWithNewFindings(analysisResult.findings)

    fun updateOnAnalysisIntermediateResult(intermediateResult: AnalysisIntermediateResult) =
        updateViewsWithNewFindings(intermediateResult.findings)

    private fun updateViewsWithNewFindings(findings: LiveFindings) {
        with(findings.onlyFor(openFiles)) {
            currentIssuesPerOpenFile.putAll(issuesPerFile)
            currentSecurityHotspotsPerOpenFile.putAll(securityHotspotsPerFile)
            ApplicationManager.getApplication().invokeLater {
                if (issuesPerFile.keys.contains(selectedFile)) {
                    updateCurrentFileTab()
                }
                updateSecurityHotspots()
                getService(project, CodeAnalyzerRestarter::class.java).refreshFiles(filesInvolved)
            }
        }
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        selectedFile = event.newFile
        updateCurrentFileTab()
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        currentIssuesPerOpenFile.remove(file)
        currentSecurityHotspotsPerOpenFile.remove(file)
        // update only security hotspots, issues will be updated in reaction to selectionChanged
        updateSecurityHotspots()
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
            ).updateCurrentFileTab(selectedFile, selectedFile?.let { currentIssuesPerOpenFile[it] })
        }
    }

    fun clearCurrentFile() {
        if (selectedFile != null) {
            currentIssuesPerOpenFile.remove(selectedFile)
        }
        updateCurrentFileTab()
    }

    private val openFiles: Set<VirtualFile>
        get() = FileEditorManager.getInstance(project).openFiles.toSet()
}