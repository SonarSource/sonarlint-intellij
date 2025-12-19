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
package org.sonarlint.intellij.editor

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.serviceContainer.NonInjectable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.runReadActionSafely
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.util.runOnPooledThread

@Service(Service.Level.PROJECT)
class CodeAnalyzerRestarter @NonInjectable internal constructor(
    private val myProject: Project, 
    private val codeAnalyzer: DaemonCodeAnalyzer,
    private val directHighlighter: DirectHighlighter
) : Disposable {
    constructor(project: Project) : this(
        project, 
        DaemonCodeAnalyzer.getInstance(project),
        SonarLintUtils.getService(project, DirectHighlighter::class.java)
    )

    // Debounce multiple rapid calls to refreshFiles
    private val pendingFiles = ConcurrentHashMap.newKeySet<VirtualFile>()
    private val scheduler = Executors.newScheduledThreadPool(1) { r -> 
        Thread(r, "sonarlint-analyzer-restarter-${myProject.name}")
    }
    private var scheduledTask: ScheduledFuture<*>? = null
    private val debounceDelayMs = 100L
    
    // Feature flag - set to true to use fast direct highlighting instead of daemon restart
    private val useDirectHighlighting = true

    fun refreshOpenFiles() {
        refreshFiles(FileEditorManager.getInstance(myProject).openFiles.toList())
    }

    fun refreshFiles(changedFiles: Collection<VirtualFile>) {
        if (changedFiles.isEmpty()) {
            return
        }
        pendingFiles.addAll(changedFiles)
        scheduledTask?.cancel(false)
        scheduledTask = scheduler.schedule({
            processPendingFiles()
        }, debounceDelayMs, TimeUnit.MILLISECONDS)
    }

    private fun processPendingFiles() {
        val filesToProcess = pendingFiles.toSet()
        pendingFiles.clear()
        
        if (filesToProcess.isEmpty()) {
            return
        }
        
        if (useDirectHighlighting) {
            // Fast path: directly update highlights without full daemon restart
            // This is much faster, especially for large files (10-15s -> <1s)
            directHighlighter.updateHighlights(filesToProcess)
        } else {
            // Slow path: restart daemon code analyzer (triggers all inspections)
            runOnPooledThread(myProject) {
                val fileEditorManager = FileEditorManager.getInstance(myProject)
                val openFiles = fileEditorManager.openFiles
                runReadActionSafely(myProject) {
                    val psiFilesToRestart = openFiles
                        .filter { it in filesToProcess }
                        .mapNotNull { getPsi(it) }
                    
                    // If we need to restart all open files, use the more efficient single call
                    if (psiFilesToRestart.size == openFiles.size) {
                        codeAnalyzer.restart()
                    } else {
                        // Restart individual files - IntelliJ's daemon will batch these internally
                        psiFilesToRestart.forEach { codeAnalyzer.restart(it) }
                    }
                }
            }
        }
    }

    private fun getPsi(virtualFile: VirtualFile): PsiFile? {
        if (!virtualFile.isValid) {
            return null
        }
        ApplicationManager.getApplication().assertReadAccessAllowed()
        return PsiManager.getInstance(myProject).findFile(virtualFile)
    }

    override fun dispose() {
        scheduledTask?.cancel(false)
        scheduler.shutdownNow()
        pendingFiles.clear()
    }
}
