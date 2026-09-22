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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.util.concurrent.CompletableFuture
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

class IntellijTerminalAdapter() : CliTerminalAdapter {
    private var terminalSession: TerminalSession = IntellijTerminalSession()

    internal constructor(terminalSession: TerminalSession) : this() {
        this.terminalSession = terminalSession
    }

    override fun launch(project: Project, command: CliCommand): TerminalLaunch {
        val shell = terminalSession.commandShell(project) ?: return TerminalLaunch.Unsupported
        return terminalSession.launch(project, CliCommandRenderer.render(command, shell))
    }

    override fun focus(handle: Any): Boolean = terminalSession.focus(handle)
}

internal interface TerminalSession {
    fun commandShell(project: Project): CommandShell?

    fun launch(project: Project, renderedCommand: String): TerminalLaunch

    fun focus(handle: Any): Boolean
}

private class IntellijTerminalSession : TerminalSession {
    override fun commandShell(project: Project): CommandShell? {
        if (!SystemInfo.isWindows) {
            return CommandShell.POSIX
        }
        val shellPath = TerminalProjectOptionsProvider.getInstance(project).shellPath.lowercase()
        return when {
            shellPath.contains("powershell") || shellPath.contains("pwsh") -> CommandShell.POWERSHELL
            shellPath.contains("bash") || shellPath.contains("zsh") || shellPath.endsWith("sh.exe") -> CommandShell.POSIX
            else -> null
        }
    }

    override fun launch(project: Project, renderedCommand: String): TerminalLaunch {
        var result: TerminalLaunch? = null
        runOnUiThread {
            result = try {
                val manager = TerminalToolWindowManager.getInstance(project)
                val widget = manager.createLocalShellWidget(project.basePath, "SonarQube CLI")
                widget.executeCommand(renderedCommand)
                TerminalLaunch.Started(
                    TerminalHandle(manager),
                    CompletableFuture.completedFuture(TerminalCompletion.ClosedWithoutExitStatus)
                )
            } catch (error: Throwable) {
                TerminalLaunch.Failed(error)
            }
        }
        return result ?: TerminalLaunch.Failed(IllegalStateException("The terminal session was not created."))
    }

    override fun focus(handle: Any): Boolean {
        if (handle !is TerminalHandle) {
            return false
        }
        runOnUiThread { handle.manager.toolWindow.show() }
        return true
    }

    private fun runOnUiThread(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            action()
        } else {
            application.invokeAndWait(action)
        }
    }
}

private data class TerminalHandle(val manager: TerminalToolWindowManager)
