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
package org.sonarlint.intellij.trigger

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings

class EventScheduler(
    private val project: Project,
    private val schedulerName: String,
    private val triggerType: TriggerType,
    private val timer: Long,
    // True -> Schedule tasks at specific intervals
    // False -> Cancel the scheduled task and reschedule a new one
    private val atInterval: Boolean
) {

    private val filesToAnalyze = mutableSetOf<VirtualFile>()
    private val scheduler = Executors.newScheduledThreadPool(1) { r -> Thread(r, "sonarlint-auto-trigger-$schedulerName-${project.name}") }
    private var scheduledTask: ScheduledFuture<*>? = null

    fun stopScheduler() {
        scheduledTask?.cancel(true)
        filesToAnalyze.clear()
        scheduler.shutdownNow()
    }

    private fun trigger() {
        if (filesToAnalyze.isNotEmpty()) {
            getService(project, AnalysisSubmitter::class.java).autoAnalyzeFiles(filesToAnalyze.toList(), triggerType)
        }
        filesToAnalyze.clear()
    }

    fun notify(file: VirtualFile) {
        if (!Settings.getGlobalSettings().isAutoTrigger) {
            return
        }

        filesToAnalyze.add(file)

        // Remove the previously finished task
        if (scheduledTask?.isDone == true) {
            scheduledTask = null
        }

        if (atInterval) {
            scheduledTask ?: let {
                // Schedule new task only if no task currently scheduled
                scheduledTask = scheduler.schedule({ trigger() }, timer, TimeUnit.MILLISECONDS)
            }
        } else {
            // Cancelling the scheduled task and postponing it later
            scheduledTask?.cancel(false)
            scheduledTask = scheduler.schedule({ trigger() }, timer, TimeUnit.MILLISECONDS)
        }
    }

}
