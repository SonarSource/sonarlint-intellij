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
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.ui.TerminalWidget
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

    override fun shellFor(project: Project): CommandShell? = terminalSession.commandShell(project)
}

internal interface TerminalSession {
    fun commandShell(project: Project): CommandShell?

    fun launch(project: Project, renderedCommand: String): TerminalLaunch
}

private class IntellijTerminalSession : TerminalSession {
    override fun commandShell(project: Project): CommandShell? {
        val shellPath = readConfiguredShellPath(project) ?: return osDefaultCommandShell()
        return classifyTerminalShell(shellPath)
    }

    override fun launch(project: Project, renderedCommand: String): TerminalLaunch {
        var result: TerminalLaunch? = null
        runOnUiThread {
            result = try {
                check(!project.isDisposed) { "The project was closed." }
                val manager = TerminalToolWindowManager.getInstance(project)
                val widget = manager.createShellWidget(project.basePath, "SonarQube CLI", true, true)
                val completion = observeTerminalCompletion(widget)
                widget.writePlainMessage("Close this terminal tab when the command finishes to continue SonarQube CLI setup.\r\n")
                widget.sendCommandToExecute(renderedCommand)
                TerminalLaunch.Started(
                    focus = {
                        runOnUiThread {
                            val content = manager.getContainer(widget)?.content ?: return@runOnUiThread
                            manager.toolWindow.contentManager.setSelectedContent(content)
                            manager.toolWindow.show { widget.requestFocus() }
                        }
                    },
                    completion = completion
                )
            } catch (error: Throwable) {
                TerminalLaunch.Failed(error)
            }
        }
        return result ?: TerminalLaunch.Failed(IllegalStateException("The terminal session was not created."))
    }

    private fun readConfiguredShellPath(project: Project): String? = try {
        TerminalProjectOptionsProvider.getInstance(project).shellPath.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
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

internal fun observeTerminalCompletion(widget: TerminalWidget): CompletableFuture<TerminalCompletion> {
    val completion = CompletableFuture<TerminalCompletion>()
    widget.addTerminationCallback({ completion.complete(TerminalCompletion.ClosedWithoutExitStatus) }, widget)
    Disposer.register(widget) { completion.complete(TerminalCompletion.ClosedWithoutExitStatus) }
    return completion
}

internal fun classifyTerminalShell(shellPath: String): CommandShell? {
    val fileName = shellPath.lowercase().substringAfterLast('/').substringAfterLast('\\')
    return when {
        "powershell" in fileName || "pwsh" in fileName -> CommandShell.POWERSHELL
        "bash" in fileName || "zsh" in fileName || fileName == "sh" || fileName == "sh.exe" -> CommandShell.POSIX
        else -> null
    }
}
