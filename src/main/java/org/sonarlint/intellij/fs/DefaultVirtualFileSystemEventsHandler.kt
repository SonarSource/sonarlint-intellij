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
package org.sonarlint.intellij.fs

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCoreUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.serviceContainer.NonInjectable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.sonarlint.intellij.common.util.FileUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile
import org.sonarlint.intellij.util.SonarLintAppUtils.visitAndAddAllChildren
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent

open class DefaultVirtualFileSystemEventsHandler @NonInjectable constructor(private val executorService: ExecutorService) : VirtualFileSystemEventsHandler, Disposable {

    // default constructor used for the application service instantiation
    // keep events in order with a single thread executor
    constructor() : this(Executors.newSingleThreadExecutor { r -> Thread(r, "sonarlint-vfs-events-notifier") })

    override fun forwardEventsAsync(events: List<VFileEvent>, eventTypeConverter: (VFileEvent) -> ModuleFileEvent.Type?) {
        executorService.submit { forwardEvents(events, eventTypeConverter) }
    }

    private fun forwardEvents(
        events: List<VFileEvent>,
        eventTypeConverter: (VFileEvent) -> ModuleFileEvent.Type?,
    ) {
        
        val openProjects = ProjectManager.getInstance().openProjects.filter { !it.isDisposed }.toList()
        val filesByModule = fileEventsByModules(events, openProjects, eventTypeConverter)
        val allFilesByModule = filesByModule.entries.associate { it.key to it.value.toList() }
        if (allFilesByModule.isNotEmpty()) {
            getService(BackendService::class.java).updateFileSystem(allFilesByModule)
        }
    }

    private fun fileEventsByModules(
        events: List<VFileEvent>,
        openProjects: List<Project>,
        eventTypeConverter: (VFileEvent) -> ModuleFileEvent.Type?,
    ): Map<Module, List<VirtualFileEvent>> {
        val map: MutableMap<Module, List<VirtualFileEvent>> = mutableMapOf()
        for (event in events) {
            // call event.file only once as it can be hurting performance
            val file = event.file ?: continue
            if (ProjectCoreUtil.isProjectOrWorkspaceFile(file)) continue
            val fileModule = findModule(file, openProjects) ?: continue
            if (!FileUtils.Companion.isFileValidForSonarLintWithExtensiveChecks(file, fileModule.project)) continue
            val fileInvolved = if (event is VFileCopyEvent) event.findCreatedFile() else file
            fileInvolved ?: continue
            val type = eventTypeConverter(event) ?: continue
            val moduleEvents = map[fileModule] ?: emptyList()
            map[fileModule] = moduleEvents + allEventsFor(fileInvolved, fileModule, type)
        }
        return map
    }

    private fun allEventsFor(
        file: VirtualFile,
        fileModule: Module,
        type: ModuleFileEvent.Type,
    ): List<VirtualFileEvent> {
        return visitAndAddAllChildren(file, fileModule.project).mapNotNull { VirtualFileEvent(type, it) }
    }

    private fun findModule(file: VirtualFile?, openProjects: List<Project>): Module? {
        file ?: return null
        return openProjects.asSequence()
            .filter { !it.isDisposed }
            .map { findModuleForFile(file, it) }
            .find { it != null }
    }

    override fun dispose() {
        executorService.shutdownNow()
    }
}
