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
package org.sonarlint.intellij.ai

import com.intellij.openapi.project.Project
import com.intellij.openapi.extensions.ExtensionPointName
import java.util.concurrent.CompletableFuture

interface CliTerminalAdapter {
    fun launch(project: Project, command: CliCommand): TerminalLaunch

    fun focus(handle: Any): Boolean
}

sealed interface TerminalLaunch {
    data class Started(val handle: Any, val completion: CompletableFuture<TerminalCompletion>) : TerminalLaunch
    data object Unsupported : TerminalLaunch
    data class Failed(val error: Throwable) : TerminalLaunch
}

sealed interface TerminalCompletion {
    data class Exited(val exitCode: Int) : TerminalCompletion
    data object Cancelled : TerminalCompletion
    data object ClosedWithoutExitStatus : TerminalCompletion
}

class SafeOptionalTerminalAdapter : CliTerminalAdapter {
    override fun launch(project: Project, command: CliCommand): TerminalLaunch = TerminalLaunch.Unsupported

    override fun focus(handle: Any): Boolean = false
}

object CliTerminalAdapterProvider {
    private val extensionPoint = ExtensionPointName.create<CliTerminalAdapter>("org.sonarlint.idea.cliTerminalAdapter")

    fun create(): CliTerminalAdapter = select(extensionPoint.extensionList)

    internal fun select(adapters: List<CliTerminalAdapter>): CliTerminalAdapter =
        adapters.firstOrNull() ?: SafeOptionalTerminalAdapter()
}
