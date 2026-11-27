/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.fixtures

import java.time.Duration
import java.util.concurrent.TimeUnit

class PluginThreadLeakTracker {
    private val baseline = pluginWorkers().mapTo(HashSet(), Thread::threadId)

    fun assertNoLeaks() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        newWorkers().forEach { worker ->
            val remaining = deadline - System.nanoTime()
            if (remaining > 0) {
                worker.join(Duration.ofNanos(remaining))
            }
        }
        val survivors = newWorkers()
        if (survivors.isNotEmpty()) {
            throw AssertionError(survivors.sortedBy(Thread::threadId).joinToString("\n", "SonarLint worker threads survived teardown:\n") { worker ->
                worker.stackTrace.joinToString("\n    at ", "  ${worker.name} [id=${worker.threadId()}]\n    at ")
            })
        }
    }

    private fun newWorkers(): List<Thread> = pluginWorkers().filter { it.threadId() !in baseline }

    private fun pluginWorkers(): List<Thread> = Thread.getAllStackTraces().keys.filter {
        it.isAlive && (it.name.startsWith("sonarlint-auto-trigger-") ||
            it.name.startsWith("sonarlint-direct-highlighter-") ||
            it.name == "sonarlint-vfs-events-notifier")
    }
}
