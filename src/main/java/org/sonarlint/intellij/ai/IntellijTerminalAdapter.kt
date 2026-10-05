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

import com.intellij.execution.CommandLineUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.util.concurrency.AppExecutorUtil
import com.pty4j.PtyProcess
import java.util.concurrent.CompletableFuture
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalTabState
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.computeInEDT

class IntellijTerminalAdapter : CliTerminalAdapter {
    override fun launch(project: Project, command: CliCommand): TerminalLaunch =
        ApplicationManager.getApplication().computeInEDT {
            try {
                check(!project.isDisposed) { "The project was closed." }
                val manager = TerminalToolWindowManager.getInstance(project)
                val runner = CliTerminalRunner(project, command)
                manager.createNewSession(runner, runner.tabState)
                val widget = runner.widget
                val completion = observeTerminalCompletion(widget, runner.completion)
                TerminalLaunch.Started(
                    focus = {
                        runOnUiThread(project) {
                            val content = manager.getContainer(widget)?.content ?: return@runOnUiThread
                            manager.toolWindow.contentManager.setSelectedContent(content)
                            manager.toolWindow.show { widget.requestFocus() }
                        }
                    },
                    completion = completion
                )
            } catch (error: Exception) {
                TerminalLaunch.Failed(error)
            }
        }
}

internal class CliTerminalRunner(project: Project, command: CliCommand) : LocalTerminalDirectRunner(project) {
    val completion = CompletableFuture<TerminalCompletion>()
    val tabState = TerminalTabState().apply {
        myTabName = "SonarQube CLI"
        myWorkingDirectory = project.basePath
        myShellCommand = listOf(command.executable) + command.arguments
    }
    lateinit var widget: TerminalWidget
        private set

    override fun createShellTerminalWidget(parent: Disposable, startupOptions: ShellStartupOptions): TerminalWidget =
        super.createShellTerminalWidget(parent, startupOptions).also { widget = it }

    override fun createProcess(startupOptions: ShellStartupOptions): PtyProcess = try {
        super.createProcess(startupOptions.builder()
            .shellCommand(CommandLineUtil.toCommandLine(requireNotNull(startupOptions.shellCommand)))
            .build())
    } catch (error: Exception) {
        completion.completeExceptionally(error)
        throw error
    }

    override fun enableShellIntegration(): Boolean = false

    override fun isTerminalSessionPersistent(): Boolean = false
}

internal fun observeTerminalCompletion(
    widget: TerminalWidget,
    completion: CompletableFuture<TerminalCompletion> = CompletableFuture()
): CompletableFuture<TerminalCompletion> {
    widget.ttyConnectorAccessor.executeWithTtyConnector { connector ->
        CompletableFuture.supplyAsync({ connector.waitFor() }, AppExecutorUtil.getAppExecutorService())
            .whenComplete { exitCode, error ->
                if (error != null) {
                    completion.completeExceptionally(error)
                } else {
                    completion.complete(TerminalCompletion.Exited(exitCode))
                }
            }
    }
    Disposer.register(widget) {
        val exitCode = try {
            ShellTerminalWidget.getProcessTtyConnector(widget.ttyConnector)?.process?.exitValue()
        } catch (_: IllegalThreadStateException) {
            null
        }
        completion.complete(exitCode?.let { TerminalCompletion.Exited(it) } ?: TerminalCompletion.ClosedWithoutExitStatus)
    }
    return completion
}
