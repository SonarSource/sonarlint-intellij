/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.fs

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

open class ModuleFileEventsNotifier : Disposable {

    open fun notifyAsync(engine: SonarLintEngine, module: Module, events: List<ClientModuleFileEvent>) {
        if (events.isEmpty()) return
        executor.submit {
            notify(engine, module, events)
        }
    }

    fun notify(engine: SonarLintEngine, module: Module, events: List<ClientModuleFileEvent>) {
        SonarLintConsole.get(module.project).info("Processing ${events.size} file system events");
        events.forEach {
            try {
                engine.fireModuleFileEvent(module, it)
            } catch (e: Exception) {
                SonarLintConsole.get(module.project).error("Error notifying analyzer of a file event", e)
            }
        }
    }

    override fun dispose() {
        executor.shutdownNow()
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        fun isPython(file: VirtualFile): Boolean {
            return file.path.endsWith(".py")
        }
    }
}
