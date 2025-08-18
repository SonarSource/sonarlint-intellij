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
package org.sonarlint.intellij.trigger

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.collections.immutable.toImmutableMap
import org.sonarlint.intellij.common.util.FileUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fs.VirtualFileEvent
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

class EventScheduler(
    private val schedulerName: String,
    private val timer: Long,
    // True -> Schedule tasks at specific intervals
    // False -> Cancel the scheduled task and reschedule a new one
    private val atInterval: Boolean
) {

    private val changedFiles = mutableSetOf<VirtualFile>()
    private val scheduler = Executors.newScheduledThreadPool(1) { r -> Thread(r, "sonarlint-auto-trigger-$schedulerName") }
    private var scheduledTask: ScheduledFuture<*>? = null

    fun stopScheduler() {
        scheduledTask?.cancel(true)
        changedFiles.clear()
        scheduler.shutdownNow()
    }

    private fun trigger() {
        groupByProject(changedFiles).forEach { (project, files) -> notifyFileChangesForProject(project, files) }
        changedFiles.clear()
    }

    fun notify(file: VirtualFile) {
        changedFiles.add(file)

        // Remove the previously finished task
        if (scheduledTask?.isDone == true) {
            scheduledTask = null
        }

        if (atInterval) {
            scheduledTask ?: let {
                // Schedule a new task only if no task currently scheduled
                scheduledTask = scheduler.schedule({ trigger() }, timer, TimeUnit.MILLISECONDS)
            }
        } else {
            // Cancelling the scheduled task and postponing it later
            scheduledTask?.cancel(false)
            scheduledTask = scheduler.schedule({ trigger() }, timer, TimeUnit.MILLISECONDS)
        }
    }

    private fun groupByProject(files: Set<VirtualFile>) =
        files.fold(mutableMapOf<Project, MutableSet<VirtualFile>>()) { acc, file ->
            ProjectLocator.getInstance().getProjectsForFile(file)
                .filter { it != null && !it.isDisposed }
                .forEach { project -> acc.computeIfAbsent(project!!) { mutableSetOf() }.add(file) }
            acc
        }.toImmutableMap()

    private fun notifyFileChangesForProject(project: Project, changedFiles: Set<VirtualFile>) {
        val filesToSendPerModule = HashMap<Module, MutableList<VirtualFileEvent>>()

        changedFiles
            .filter { FileUtils.isFileValidForSonarLintWithExtensiveChecks(it, project) }
            .forEach { file ->
                val module = findModuleForFile(file, project) ?: return@forEach
                filesToSendPerModule.computeIfAbsent(module) { mutableListOf() }.add(VirtualFileEvent(ModuleFileEvent.Type.MODIFIED, file))
            }

        if (filesToSendPerModule.isNotEmpty()) {
            getService(BackendService::class.java).updateFileSystem(filesToSendPerModule, true)
        }
    }

}
