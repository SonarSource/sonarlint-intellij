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
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.ui.TerminalWidget
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.sonarlint.intellij.AbstractSonarLintLightTests

class IntellijTerminalAdapterTests : AbstractSonarLintLightTests() {
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
    fun `selects the terminal plugin adapter`() {
        assertThat(CliTerminalAdapterProvider.create()).isInstanceOf(IntellijTerminalAdapter::class.java)
    }

    @Test
    fun `CLI sessions are not restored when the project reopens`() {
        val runner = CliTerminalRunner(project, CliCommand("sonar", listOf("auth", "login"), true))

        assertThat(runner.isTerminalSessionPersistent()).isFalse()
    }

    @Test
    fun `prepared shell commands are not rewritten by shell integration`() {
        val command = CliCommand("powershell.exe", listOf("-NoProfile", "-Command", "Write-Output 'two words'"), false)
        val runner = CliTerminalRunner(project, command)
        val options = runner.configureStartupOptions(ShellStartupOptions.Builder()
            .shellCommand(runner.tabState.myShellCommand)
            .workingDirectory(project.basePath)
            .build())

        assertThat(options.shellCommand).containsExactly(command.executable, *command.arguments.toTypedArray())
        assertThat(options.shellIntegration).isNull()
    }

    @Test
    fun `direct runner preserves arguments and interactive input`(@TempDir directory: Path) {
        val source = Files.createDirectories(directory.resolve("path with spaces")).resolve("ArgvProbe.java")
        Files.writeString(source, """
            class ArgvProbe {
                public static void main(String[] arguments) throws Exception {
                    var encoder = java.util.Base64.getEncoder();
                    var encoded = new java.util.ArrayList<String>();
                    for (String argument : arguments) {
                        encoded.add(encoder.encodeToString(argument.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    }
                    System.out.println("arguments=" + String.join(",", encoded));
                    var input = new java.io.BufferedReader(new java.io.InputStreamReader(
                        System.in, java.nio.charset.StandardCharsets.UTF_8)).readLine();
                    System.out.println("input=" + input);
                }
            }
        """.trimIndent())
        val javaExecutable = Path.of(System.getProperty("java.home"), "bin", if (SystemInfo.isWindows) "java.exe" else "java")
        val arguments = listOf("", "two words", "a'b", "a\"b", "a\\\"b", "trailing\\", "a\u2019b", "$&|;()!", "line1\nline2")
        val command = CliCommand(javaExecutable.toString(), listOf(source.toString()) + arguments, true)
        val runner = CliTerminalRunner(project, command)
        val options = runner.configureStartupOptions(ShellStartupOptions.Builder()
            .shellCommand(runner.tabState.myShellCommand)
            .workingDirectory(directory.toString())
            .build())
        val process = runner.createProcess(options)
        try {
            process.outputStream.write("ready\n".toByteArray(Charsets.UTF_8))
            process.outputStream.flush()
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue()
            val output = process.inputStream.bufferedReader().readLines()
            assertThat(process.exitValue()).withFailMessage(output.joinToString("\n")).isZero()
            val encodedArguments = arguments.joinToString(",") {
                Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
            }
            assertThat(output).contains("arguments=$encodedArguments", "input=ready")
        } finally {
            process.destroy()
        }
    }
}
