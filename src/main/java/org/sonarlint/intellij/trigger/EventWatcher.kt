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
import java.util.concurrent.ConcurrentHashMap
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.analysis.Cancelable
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.util.SonarLintAppUtils.retainOpenFiles
import org.sonarlint.intellij.util.runOnPooledThread

class EventWatcher(
    private val project: Project,
    watcherName: String,
    private val eventMap: ConcurrentHashMap<VirtualFile, Long>,
    private val triggerType: TriggerType
) : Thread() {

    private var stop: Boolean = false
    private var task: Cancelable? = null

    companion object {
        const val DEFAULT_TIMER_MS = 2000
    }

    init {
        isDaemon = true
        name = "sonarlint-auto-trigger-$watcherName-${project.name}"
    }

    fun stopWatcher() {
        stop = true
        interrupt()
    }

    override fun run() {
        while (!stop) {
            checkTimers()
            try {
                sleep(200)
            } catch (e: InterruptedException) {
                // continue until stop flag is set
            }
        }
    }

    private fun triggerFiles(files: List<VirtualFile>) {
        if (Settings.getGlobalSettings().isAutoTrigger) {
            val openFilesToAnalyze = retainOpenFiles(project, files)
            if (openFilesToAnalyze.isNotEmpty()) {
                task?.let {
                    it.cancel()
                    task = null
                    return
                }
                files.forEach { eventMap.remove(it) }
                task = getService(project, AnalysisSubmitter::class.java).autoAnalyzeFiles(openFilesToAnalyze, triggerType)
            }
        }
    }

    private fun checkTimers() {
        val now = System.currentTimeMillis()

        val it = eventMap.entries.iterator()
        val filesToTrigger = ArrayList<VirtualFile>()
        while (it.hasNext()) {
            val event = it.next()
            if (!event.key.isValid) {
                it.remove()
                continue
            }
            // don't trigger if file currently has errors?
            // filter files opened in the editor
            // use some heuristics based on analysis time or average pauses? Or make it configurable?
            if (event.value + DEFAULT_TIMER_MS < now) {
                filesToTrigger.add(event.key)
            }
        }
        runOnPooledThread(project) { triggerFiles(filesToTrigger) }
    }

}
