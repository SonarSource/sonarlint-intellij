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
package org.sonarlint.intellij.tasks

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.analysis.GlobalBackgroundTaskTracker
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService

class GlobalTaskProgressReporter(
    project: Project?,
    title: String,
    private val totalTasks: Int,
    private val withTextUpdate: Boolean
) : TaskProgressReporter(project, title, true, true) {

    private val modulesPerTaskId = mutableMapOf<String, Module>()
    private val modulesDone = mutableListOf<Module>()
    private var isCancelled = false

    init {
        updateText("SonarQube: Analysis 1 out of $totalTasks modules")
    }

    override fun onCancel() {
        isCancelled = true
        modulesPerTaskId.keys.forEach { id ->
            getService(BackendService::class.java).cancelTask(id)
        }
        super.onCancel()
    }

    fun cancelAllTasks() {
        isCancelled = true
        modulesPerTaskId.keys.forEach { id ->
            getService(BackendService::class.java).cancelTask(id)
        }
    }

    fun updateText(text: String) {
        if (withTextUpdate) {
            this.progressIndicator?.text = text
        }
    }

    fun trackTask(module: Module, taskId: String?) {
        taskId?.let { modulesPerTaskId[taskId] = module } ?: modulesDone.add(module)
        checkIfGloballyFinished()
    }

    fun hasTaskId(taskId: String): Boolean {
        return modulesPerTaskId.contains(taskId)
    }

    fun taskFinished(taskId: String) {
        val module = modulesPerTaskId[taskId] ?: return
        modulesPerTaskId.remove(taskId)
        modulesDone.add(module)
        updateText("SonarQube: Analysis ${modulesDone.size} out of $totalTasks modules")
        checkIfGloballyFinished()
    }

    fun getCancelledTaskIds(): Set<String> {
        return if (isCancelled) {
            modulesPerTaskId.keys
        } else {
            emptySet()
        }
    }

    private fun checkIfGloballyFinished() {
        if (modulesDone.size == totalTasks) {
            complete()
            getService(GlobalBackgroundTaskTracker::class.java).untrackGlobalTask(this)
        }
    }

}
