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

import com.intellij.openapi.components.Service
import java.util.concurrent.ConcurrentLinkedQueue
import org.sonarlint.intellij.tasks.GlobalTaskProgressReporter

@Service(Service.Level.APP)
class GlobalBackgroundTaskTracker() {

    private val backgroundTasks = ConcurrentLinkedQueue<GlobalTaskState>()

    fun track(totalTasks: Int, backgroundTask: GlobalTaskProgressReporter): GlobalTaskState {
        val backgroundTaskState = GlobalTaskState(totalTasks, backgroundTask)
        backgroundTasks.add(backgroundTaskState)
        return backgroundTaskState
    }

    fun findBackgroundTask(taskId: String): GlobalTaskState? {
        return backgroundTasks.find { it.hasTaskId(taskId) }
    }

    fun findBackgroundTask(taskReporter: GlobalTaskProgressReporter): GlobalTaskState? {
        return backgroundTasks.find { it.hasTaskReporter(taskReporter) }
    }

    fun finish(backgroundTaskState: GlobalTaskState) {
        backgroundTasks.remove(backgroundTaskState)
    }

    fun isTaskIdCancelled(taskId: String): Boolean {
        return backgroundTasks.any { it.getCancelledIds().contains(taskId) }
    }

}
