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

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicBoolean
import org.sonarlint.intellij.analysis.GlobalBackgroundTaskTracker
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ProgressUpdateNotification

open class TaskProgressReporter(
    project: Project?,
    title: String,
    isCancellable: Boolean,
    private val isIndeterminate: Boolean,
    private val message: String? = null,
    private val taskId: String? = null,
    private val onCompletion: () -> Unit = {}
) : Task.Backgroundable(project, "SonarQube: $title", isCancellable, ALWAYS_BACKGROUND) {
    var progressIndicator: ProgressIndicator? = null
    private val waitMonitor = Object()
    private val complete = AtomicBoolean(false)

    override fun run(indicator: ProgressIndicator) {
        progressIndicator = indicator
        progressIndicator!!.isIndeterminate = isIndeterminate
        message?.let { progressIndicator!!.text = it }
        while (!complete.get()) {
            if (indicator.isCanceled) {
                complete.set(true)
                break
            }
            try {
                synchronized(waitMonitor) {
                    waitMonitor.wait(1000)
                }
            } catch (_: InterruptedException) {
                complete.set(true)
            }
        }
        onCompletion()
    }

    override fun onCancel() {
        taskId?.let {
            getService(BackendService::class.java).cancelTask(it)
            getService(GlobalBackgroundTaskTracker::class.java).finishTask(it)
        }
        super.onCancel()
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
