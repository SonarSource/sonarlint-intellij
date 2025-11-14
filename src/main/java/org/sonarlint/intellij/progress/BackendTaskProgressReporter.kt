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
package org.sonarlint.intellij.progress

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import java.util.concurrent.ConcurrentHashMap
import org.sonarlint.intellij.analysis.GlobalBackgroundTaskTracker
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.tasks.TaskProgressReporter
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ProgressUpdateNotification
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams

class BackendTaskProgressReporter {
    private val taskPool = ConcurrentHashMap<String, TaskProgressReporter>()

    fun startTask(params: StartProgressParams) {
        val taskId = params.taskId
        if (taskPool.containsKey(taskId)) {
            val errorMessage = "Task with ID $taskId is already active, skip reporting it"
            GlobalLogOutput.get().log(errorMessage, ClientLogOutput.Level.DEBUG)
            return
        }
        if (getService(GlobalBackgroundTaskTracker::class.java).isTaskIdCancelled(taskId)) {
            val errorMessage = "Task with ID $taskId was cancelled"
            GlobalLogOutput.get().log(errorMessage, ClientLogOutput.Level.DEBUG)
            getService(BackendService::class.java).cancelTask(taskId)
            getService(GlobalBackgroundTaskTracker::class.java).finishTask(taskId)
            return
        }
        val project = params.configurationScopeId?.let {
            BackendService.findModule(it)?.project
                ?: BackendService.findProject(it)
        }
        val task = TaskProgressReporter(
            project, params.title, params.isCancellable,
            params.isIndeterminate, params.message, taskId, onCompletion = {
                taskPool.remove(taskId)
            })
        taskPool[taskId] = task
        if (ApplicationManager.getApplication().isUnitTestMode) {
            // in headless mode the task is run on the same thread, run on a pooled thread instead
            ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, EmptyProgressIndicator())
        } else {
            task.queue()
        }
    }

    fun updateProgress(taskId: String, notification: ProgressUpdateNotification) {
        val task = taskPool[taskId]
        if (task == null) {
            GlobalLogOutput.get().log("Task with ID $taskId is unknown, skip reporting it", ClientLogOutput.Level.DEBUG)
            return
        }
        task.updateProgress(notification)
    }

    fun completeTask(taskId: String) {
        val task = taskPool[taskId]
        if (task == null) {
            GlobalLogOutput.get().log("Task with ID $taskId is unknown, skip reporting it", ClientLogOutput.Level.DEBUG)
            return
        }
        task.complete()
        // The task may be included as part of a Global Background Task
        getService(GlobalBackgroundTaskTracker::class.java).finishTask(taskId)
    }
}
