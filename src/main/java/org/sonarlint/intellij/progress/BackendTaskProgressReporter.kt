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
package org.sonarlint.intellij.progress

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ProgressUpdateNotification
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams

class BackendTaskProgressReporter {
    private val taskPool = ConcurrentHashMap<String, AwaitingBackgroundTask>()

    fun startTask(params: StartProgressParams): CompletableFuture<Void> {
        val taskId = params.taskId
        if (taskPool.containsKey(taskId)) {
            val errorMessage = "Task with ID $taskId is already active, skip reporting it"
            GlobalLogOutput.get().log(errorMessage, ClientLogOutput.Level.DEBUG)
            return CompletableFuture.failedFuture(IllegalArgumentException(errorMessage))
        }
        val project = params.configurationScopeId?.let {
            BackendService.findModule(it)?.project
                ?: BackendService.findProject(it)
        }
        val taskStartedFuture = CompletableFuture<Void>()
        val task = AwaitingBackgroundTask(project, params, taskStartedFuture, onCompletion = {
            taskPool.remove(taskId)
        })
        taskPool[taskId] = task
        if (ApplicationManager.getApplication().isUnitTestMode) {
            // in headless mode the task is run on the same thread, run on a pooled thread instead
            ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, EmptyProgressIndicator())
        } else {
            task.queue()
        }
        return taskStartedFuture
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
    }
}

private class AwaitingBackgroundTask(
    project: Project?,
    private val params: StartProgressParams,
    private val taskStartedFuture: CompletableFuture<Void>,
    private val onCompletion: () -> Unit,
) :
    Task.Backgroundable(project, params.title, params.isCancellable, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
    var progressIndicator: ProgressIndicator? = null
    private val waitMonitor = Object()
    private val complete = AtomicBoolean(false)

    override fun run(indicator: ProgressIndicator) {
        progressIndicator = indicator
        progressIndicator!!.isIndeterminate = params.isIndeterminate
        params.message?.let { progressIndicator!!.text = it }
        taskStartedFuture.complete(null)
        while (!complete.get()) {
            try {
                synchronized(waitMonitor) {
                    waitMonitor.wait(60 * 1000)
                }
            } catch (_: InterruptedException) {
                complete.set(true)
            }
        }
        onCompletion()
    }


    fun updateProgress(notification: ProgressUpdateNotification) {
        synchronized(waitMonitor) {
            waitMonitor.notify()
        }
        notification.percentage?.let { percentage ->
            progressIndicator?.let {
                if (it.isIndeterminate) {
                    it.isIndeterminate = false
                }
                it.fraction = percentage.toDouble()
            }
        }
        notification.message?.let { message -> progressIndicator?.text = message }
    }

    fun complete() {
        complete.set(true)
        synchronized(waitMonitor) {
            waitMonitor.notify()
        }
    }
}
