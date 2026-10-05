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
package org.sonarlint.intellij.trigger

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import org.sonarlint.intellij.common.util.FileUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.fs.VirtualFileEvent
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

class EventScheduler internal constructor(
    private val timer: Long,
    // True -> Schedule tasks at specific intervals
    // False -> Cancel the scheduled task and reschedule a new one
    private val atInterval: Boolean,
    private val scheduler: ScheduledExecutorService,
    private val batchConsumer: (Set<VirtualFile>) -> Unit
) {

    constructor(schedulerName: String, timer: Long, atInterval: Boolean) : this(
        timer,
        atInterval,
        Executors.newScheduledThreadPool(1) { r -> Thread(r, "sonarlint-auto-trigger-$schedulerName") },
        { dispatchFileChanges(it, ProjectLocator.getInstance()) }
    )

    private val lock = Any()
    private val changedFiles = mutableSetOf<VirtualFile>()
    private var scheduledTask: ScheduledFuture<*>? = null
    private var scheduledTaskIdentity: Any? = null
    private var stopped = false

    fun stopScheduler() {
        synchronized(lock) {
            if (stopped) return
            stopped = true
            scheduledTaskIdentity = null
            scheduledTask?.cancel(true)
            scheduledTask = null
            changedFiles.clear()
        }
        scheduler.shutdownNow()
    }

    private fun trigger(identity: Any) {
        val batch = synchronized(lock) {
            if (stopped || scheduledTaskIdentity !== identity) return
            scheduledTask = null
            scheduledTaskIdentity = null
            changedFiles.toImmutableSet().also { changedFiles.clear() }
        }
        batchConsumer(batch)
    }

    fun notify(file: VirtualFile) {
        synchronized(lock) {
            if (stopped) return
            changedFiles.add(file)
            if (atInterval && scheduledTaskIdentity != null) return
            scheduledTask?.cancel(false)
            val identity = Any()
            scheduledTaskIdentity = identity
            scheduledTask = scheduler.schedule({ trigger(identity) }, timer, TimeUnit.MILLISECONDS)
        }
    }

}

internal fun dispatchFileChanges(files: Set<VirtualFile>, projectLocator: ProjectLocator) {
    groupByProject(files, projectLocator).forEach { (project, projectFiles) -> notifyFileChangesForProject(project, projectFiles) }
}

private fun groupByProject(files: Set<VirtualFile>, projectLocator: ProjectLocator) =
    files.fold(mutableMapOf<Project, MutableSet<VirtualFile>>()) { acc, file ->
        projectLocator.getProjectsForFile(file)
            .filter { it != null && !it.isDisposed }
            .forEach { project -> acc.computeIfAbsent(project!!) { mutableSetOf() }.add(file) }
        acc
    }.mapValues { it.value.toImmutableSet() }.toImmutableMap()

private fun notifyFileChangesForProject(project: Project, changedFiles: Set<VirtualFile>) {
    if (project.isDisposed) return
    val filesToSendPerModule = HashMap<Module, MutableList<VirtualFileEvent>>()

    changedFiles
        .filter { FileUtils.isFileValidForSonarLintWithExtensiveChecks(it, project) }
        .forEach { file ->
            val module = findModuleForFile(file, project) ?: return@forEach
            filesToSendPerModule.computeIfAbsent(module) { mutableListOf() }.add(VirtualFileEvent(ModuleFileEvent.Type.MODIFIED, file))
        }

    if (filesToSendPerModule.isNotEmpty() && !project.isDisposed) {
        getService(BackendService::class.java).updateFileSystem(filesToSendPerModule, true)
    }
}
