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

import com.intellij.openapi.util.Disposer
import com.intellij.terminal.ui.TerminalWidget
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.sonarlint.intellij.AbstractSonarLintLightTests

class IntellijTerminalAdapterTests : AbstractSonarLintLightTests() {
    @Test
    fun classifies_only_the_shell_executable_even_when_arguments_contain_paths() {
        assertThat(classifyTerminalShell("pwsh.exe -WorkingDirectory C:/work")).isEqualTo(CommandShell.POWERSHELL)
        assertThat(classifyTerminalShell("\"C:\\Program Files\\PowerShell\\7\\pwsh.exe\" -WorkingDirectory C:/work"))
            .isEqualTo(CommandShell.POWERSHELL)
        assertThat(classifyTerminalShell("'/opt/shell path/bash' -l")).isEqualTo(CommandShell.POSIX)
        assertThat(classifyTerminalShell("/bin/sh -l")).isEqualTo(CommandShell.POSIX)
        assertThat(classifyTerminalShell("cmd.exe /k C:\\tools\\bash-env.bat")).isNull()
        assertThat(classifyTerminalShell("cmd.exe /k C:\\tools\\pwsh.exe")).isNull()
        assertThat(classifyTerminalShell("/usr/bin/notbash")).isNull()
    }

    @Test
    fun unsupported_shell_still_provides_a_shell_for_clipboard_copy() {
        val session = mock<TerminalSession>()
        val adapter = IntellijTerminalAdapter(session)

        assertThat(adapter.launch(project, CliCommand("sonar", listOf("auth"), true))).isSameAs(TerminalLaunch.Unsupported)
        assertThat(adapter.shellFor(project)).isEqualTo(osDefaultCommandShell())
        verify(session, never()).launch(any(), any())
    }

    @Test
    fun `holds completion until the terminal session terminates`() {
        val widget = mock<TerminalWidget>()
        val completion = observeTerminalCompletion(widget)
        val termination = argumentCaptor<Runnable>()
        verify(widget).addTerminationCallback(termination.capture(), eq(widget))

        assertThat(completion).isNotDone()
        termination.firstValue.run()
        assertThat(completion.join()).isEqualTo(TerminalCompletion.ClosedWithoutExitStatus)
        Disposer.dispose(widget)
    }

    @Test
    fun `closing the terminal completes an operation without inventing an exit status`() {
        val widget = mock<TerminalWidget>()
        val completion = observeTerminalCompletion(widget)

        assertThat(completion).isNotDone()
        Disposer.dispose(widget)

        assertThat(completion.join()).isEqualTo(TerminalCompletion.ClosedWithoutExitStatus)
    }

    @Test
    fun `classifies the configured terminal shell on every operating system`() {
        assertThat(classifyTerminalShell("/usr/local/bin/pwsh")).isEqualTo(CommandShell.POWERSHELL)
        assertThat(classifyTerminalShell("\"C:\\Program Files\\PowerShell\\7\\pwsh.exe\"")).isEqualTo(CommandShell.POWERSHELL)
        assertThat(classifyTerminalShell("/bin/zsh")).isEqualTo(CommandShell.POSIX)
        assertThat(classifyTerminalShell("/bin/sh")).isEqualTo(CommandShell.POSIX)
        assertThat(classifyTerminalShell("C:\\Windows\\System32\\bash.exe")).isEqualTo(CommandShell.POSIX)
        assertThat(classifyTerminalShell("C:\\Windows\\System32\\cmd.exe")).isNull()
        assertThat(classifyTerminalShell("/usr/bin/fish")).isNull()
    }

    @Test
    fun `selects the terminal plugin adapter and preserves rendered argument boundaries`() {
        assertThat(CliTerminalAdapterProvider.create()).isInstanceOf(IntellijTerminalAdapter::class.java)
        val command = CliCommand("sonar", listOf("auth", ""), true)
        var launchedCommand: String? = null
        val session = object : TerminalSession {
            override fun commandShell(project: com.intellij.openapi.project.Project): CommandShell = CommandShell.POSIX

            override fun launch(project: com.intellij.openapi.project.Project, renderedCommand: String): TerminalLaunch {
                launchedCommand = renderedCommand
                return TerminalLaunch.Started({}, CompletableFuture.completedFuture(TerminalCompletion.ClosedWithoutExitStatus))
            }
        }

        IntellijTerminalAdapter(session).launch(project, command)

        assertThat(launchedCommand).contains("'sonar'").contains("''")
    }

}
