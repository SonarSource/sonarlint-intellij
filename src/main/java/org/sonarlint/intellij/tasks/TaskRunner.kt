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

import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.function.Consumer

fun <T> runModalTaskWithResult(project: Project?, title: String, worker: (ProgressIndicator) -> T): T {
    val task = object : Task.WithResult<T, RuntimeException>(project, title, true) {
        override fun compute(indicator: ProgressIndicator): T {
            return worker.invoke(indicator)
        }
    }
    task.queue()
    return task.result
}

fun startBackgroundableModalTask(project: Project?, title: String, worker: Consumer<ProgressIndicator>): Task {
    return startBackgroundable(project, title, worker, PerformInBackgroundOption.DEAF)
}

fun startBackgroundTask(project: Project?, title: String, worker: Consumer<ProgressIndicator>) {
    startBackgroundable(project, title, worker, PerformInBackgroundOption.ALWAYS_BACKGROUND)
}

private fun startBackgroundable(
    project: Project?,
    title: String,
    worker: Consumer<ProgressIndicator>,
    performInBackgroundOption: PerformInBackgroundOption,
): Task {
    val task = object : Task.Backgroundable(project, title, true, performInBackgroundOption) {
        override fun run(indicator: ProgressIndicator) {
            worker.accept(indicator)
        }
    }
    task.queue()
    return task
}
