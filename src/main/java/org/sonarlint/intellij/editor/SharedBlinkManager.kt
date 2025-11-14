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
package org.sonarlint.intellij.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared blinking manager that coordinates all blinking operations in a project
 * using a single alarm instead of creating one alarm per RangeBlinker instance.
 */
@Service(Service.Level.PROJECT)
class SharedBlinkManager(private val project: Project) : Disposable {

    private val activeBlinkers = ConcurrentHashMap<String, RangeBlinker>()
    private val sharedAlarm = Alarm(this)

    fun registerBlinker(id: String, blinker: RangeBlinker) {
        activeBlinkers[id] = blinker
        scheduleNextBlink()
    }

    fun unregisterBlinker(id: String) {
        activeBlinkers.remove(id)
    }

    fun stopAllBlinking() {
        sharedAlarm.cancelAllRequests()
        activeBlinkers.values.forEach { rangeBlinker -> rangeBlinker.removeHighlights() }
        activeBlinkers.clear()
    }

    private fun scheduleNextBlink() {
        if (project.isDisposed) {
            return
        }

        sharedAlarm.cancelAllRequests()
        sharedAlarm.addRequest({
            if (activeBlinkers.isNotEmpty()) {
                // Update all active blinkers
                val finishedBlinkers = ArrayList<String>()

                activeBlinkers.forEach { (id: String, blinker: RangeBlinker) ->
                    if (!blinker.performBlinkCycle()) {
                        finishedBlinkers.add(id)
                    }
                }

                // Remove finished blinkers
                finishedBlinkers.forEach { key -> activeBlinkers.remove(key) }

                // Schedule next blink if there are still active blinkers
                if (activeBlinkers.isNotEmpty()) {
                    scheduleNextBlink()
                }
            }
        }, 400)
    }

    override fun dispose() {
        stopAllBlinking()
    }

}
