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

    private val backgroundTasks = ConcurrentLinkedQueue<GlobalTaskProgressReporter>()

    fun track(backgroundTask: GlobalTaskProgressReporter) {
        backgroundTasks.add(backgroundTask)
    }

    fun finishTask(taskId: String) {
        backgroundTasks.find { it.hasTaskId(taskId) }?.taskFinished(taskId)
    }

    fun untrackGlobalTask(backgroundTaskState: GlobalTaskProgressReporter) {
        backgroundTasks.remove(backgroundTaskState)
    }

    fun isTaskIdCancelled(taskId: String): Boolean {
        return backgroundTasks.any { it.getCancelledTaskIds().contains(taskId) }
    }

}
