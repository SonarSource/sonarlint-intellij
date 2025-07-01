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

import com.intellij.openapi.module.Module
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.tasks.GlobalTaskProgressReporter
import org.sonarlint.intellij.tasks.TaskProgressReporter

class GlobalTaskState(
    private val totalTasks: Int,
    private val taskReporter: GlobalTaskProgressReporter
) {

    private val modulesPerTaskId = mutableMapOf<String, Module>()
    private val modulesDone = mutableListOf<Module>()
    private var isCancelled = false

    fun track(module: Module, taskId: String?) {
        taskId?.let { modulesPerTaskId[taskId] = module } ?: modulesDone.add(module)
        checkIfFinished()
    }

    fun hasTaskId(taskId: String): Boolean {
        return modulesPerTaskId.contains(taskId)
    }

    fun hasTaskReporter(taskReporter: TaskProgressReporter): Boolean {
        return taskReporter == this.taskReporter
    }

    fun finish(taskId: String) {
        val module = modulesPerTaskId[taskId] ?: return
        modulesPerTaskId.remove(taskId)
        modulesDone.add(module)
        taskReporter.update("SonarQube: Analysis ${modulesDone.size} Out Of $totalTasks")
        checkIfFinished()
    }

    fun cancel() {
        isCancelled = true
        modulesPerTaskId.keys.forEach { id ->
            getService(BackendService::class.java).cancelTask(id)
        }
    }

    fun getCancelledIds(): Set<String> {
        return if (isCancelled) {
            modulesPerTaskId.keys
        } else {
            emptySet()
        }
    }

    private fun checkIfFinished() {
        if (modulesDone.size == totalTasks) {
            taskReporter.complete()
            getService( GlobalBackgroundTaskTracker::class.java).finish(this)
        }
    }

}
