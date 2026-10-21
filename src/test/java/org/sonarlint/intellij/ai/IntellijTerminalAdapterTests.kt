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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.terminal.ui.TtyConnectorAccessor
import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.TerminalKeyEncoder
import com.jediterm.terminal.TtyConnector
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.jetbrains.plugins.terminal.AbstractTerminalRunner
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.sonarlint.intellij.AbstractSonarLintLightTests

class IntellijTerminalAdapterTests : AbstractSonarLintLightTests() {
    @Test
    fun `a disposed project cannot launch a terminal on the EDT`() {
        val closedProject = mock<Project>()
        whenever(closedProject.isDisposed).thenReturn(true)

        val launch = IntellijTerminalAdapter().launch(closedProject, CliCommand("sonar", listOf("auth"), true))

        assertThat(launch).isInstanceOf(TerminalLaunch.Failed::class.java)
        assertThat((launch as TerminalLaunch.Failed).error).hasMessage("The project was closed.")
    }

    @Test
    fun `waits for the terminal connector to become available`() {
        val widget = mock<TerminalWidget>()
        val accessor = TtyConnectorAccessor()
        whenever(widget.ttyConnectorAccessor).thenReturn(accessor)
        val connector = mock<TtyConnector>()
        whenever(connector.waitFor()).thenReturn(0)
        val completion = observeTerminalCompletion(widget)

        assertThat(completion).isNotDone()
        accessor.ttyConnector = connector

        assertThat(completion.get(10, TimeUnit.SECONDS)).isEqualTo(TerminalCompletion.Exited(0))
        Disposer.dispose(widget)
    }

    @Test
    fun `closing the terminal completes an operation without inventing an exit status`() {
        val widget = mock<TerminalWidget>()
        whenever(widget.ttyConnectorAccessor).thenReturn(TtyConnectorAccessor())
        val completion = observeTerminalCompletion(widget)

        assertThat(completion).isNotDone()
        Disposer.dispose(widget)

        assertThat(completion.join()).isEqualTo(TerminalCompletion.ClosedWithoutExitStatus)
    }

    @Test
    fun `closing an exited terminal preserves its exit code before the waiter reports`() {
        val process = mock<Process>()
        whenever(process.exitValue()).thenReturn(17)
        val connector = mock<ProcessTtyConnector>()
        whenever(connector.process).thenReturn(process)
        val widget = mock<TerminalWidget>()
        whenever(widget.ttyConnectorAccessor).thenReturn(mock())
        whenever(widget.ttyConnector).thenReturn(connector)
        val completion = observeTerminalCompletion(widget)

        Disposer.dispose(widget)

        assertThat(completion.join()).isEqualTo(TerminalCompletion.Exited(17))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `process startup failure completes the operation without a terminal connector`(useStartupOptions: Boolean, @TempDir directory: Path) {
        val runner = CliTerminalRunner(project, CliCommand(directory.resolve("missing-executable").toString(), emptyList(), true))
        val widget = mock<TerminalWidget>()
        whenever(widget.ttyConnectorAccessor).thenReturn(TtyConnectorAccessor())
        val completion = observeTerminalCompletion(widget, runner.completion)
        val options = runner.configureStartupOptions(ShellStartupOptions.Builder()
            .shellCommand(runner.tabState.myShellCommand)
            .workingDirectory(directory.toString())
            .build())
        try {
            assertThatThrownBy {
                if (useStartupOptions) runner.createTtyConnector(options) else runner.createProcess(options)
            }.isInstanceOf(ExecutionException::class.java)

            assertThat(completion).isCompletedExceptionally()
        } finally {
            Disposer.dispose(widget)
        }
    }

    @ParameterizedTest
    @CsvSource("0, false", "0, true", "17, false", "17, true")
    fun `reports the actual exit code from the direct runner`(exitCode: Int, useStartupOptions: Boolean, @TempDir directory: Path) {
        val source = directory.resolve("ExitProbe.java")
        Files.writeString(source, """
            class ExitProbe {
                public static void main(String[] arguments) throws Exception {
                    System.in.read();
                    System.exit(Integer.parseInt(arguments[0]));
                }
            }
        """.trimIndent())
        val javaExecutable = Path.of(System.getProperty("java.home"), "bin", if (SystemInfo.isWindows) "java.exe" else "java")
        val runner = CliTerminalRunner(project, CliCommand(javaExecutable.toString(), listOf(source.toString(), exitCode.toString()), true))
        val options = runner.configureStartupOptions(ShellStartupOptions.Builder()
            .shellCommand(runner.tabState.myShellCommand)
            .workingDirectory(directory.toString())
            .build())
        val connector = if (useStartupOptions) runner.createTtyConnector(options) else runner.createTtyConnector(runner.createProcess(options))
        val process = requireNotNull(ShellTerminalWidget.getProcessTtyConnector(connector)).process
        val widget = mock<TerminalWidget>()
        val accessor = TtyConnectorAccessor().apply { ttyConnector = connector }
        whenever(widget.ttyConnectorAccessor).thenReturn(accessor)
        val completion = observeTerminalCompletion(widget)
        try {
            assertThat(completion).isNotDone()
            process.outputStream.write("ready".toByteArray(Charsets.UTF_8))
            process.outputStream.write(TerminalKeyEncoder().getCode(KeyEvent.VK_ENTER, 0))
            process.outputStream.flush()

            assertThat(completion.get(30, TimeUnit.SECONDS)).isEqualTo(TerminalCompletion.Exited(exitCode))
            Disposer.dispose(widget)
            assertThat(completion.join()).isEqualTo(TerminalCompletion.Exited(exitCode))
        } finally {
            connector.close()
            Disposer.dispose(widget)
        }
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
    fun `modern startup receives raw Windows arguments once`() {
        val command = CliCommand("C:\\Program Files\\SonarQube\\sonar.exe", listOf("", "two words", "a\"b", "trailing\\", "C:\\path with spaces\\"), true)
        val connector = mock<TtyConnector>()
        val receivedOptions = mutableListOf<ShellStartupOptions>()
        val delegate = StartupOptionsTerminalRunner(project) {
            receivedOptions.add(it)
            connector
        }
        val method = StartupOptionsTerminalRunner::class.java.getMethod("createTtyConnector", ShellStartupOptions::class.java)
        val runner = CliTerminalRunner(project, command, delegate, method)
        val options = ShellStartupOptions.Builder().shellCommand(runner.tabState.myShellCommand).build()

        mockStatic(CommandLineUtil::class.java).use { commandLineUtil ->
            assertThat(runner.createTtyConnector(options)).isSameAs(connector)
            commandLineUtil.verifyNoInteractions()
        }

        assertThat(receivedOptions).containsExactly(options)
        assertThat(receivedOptions.single().shellCommand).containsExactly(command.executable, *command.arguments.toTypedArray())
    }

    @Test
    fun `modern startup failure preserves the original exception`() {
        val failure = IllegalStateException("Process startup failed")
        var calls = 0
        val delegate = StartupOptionsTerminalRunner(project) {
            calls++
            throw failure
        }
        val method = StartupOptionsTerminalRunner::class.java.getMethod("createTtyConnector", ShellStartupOptions::class.java)
        val runner = CliTerminalRunner(project, CliCommand("sonar", emptyList(), true), delegate, method)
        val options = ShellStartupOptions.Builder().shellCommand(runner.tabState.myShellCommand).build()

        assertThatThrownBy { runner.createTtyConnector(options) }.isSameAs(failure)

        assertThat(calls).isEqualTo(1)
        assertThat(runner.completion).isCompletedExceptionally()
        assertThatThrownBy { runner.completion.join() }.cause().isSameAs(failure)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `direct runner preserves arguments and interactive input`(useStartupOptions: Boolean, @TempDir directory: Path) {
        val source = Files.createDirectories(directory.resolve("path with spaces")).resolve("ArgvProbe.java")
        val result = source.parent.resolve("result.txt")
        Files.writeString(source, """
            class ArgvProbe {
                public static void main(String[] arguments) throws Exception {
                    var encoder = java.util.Base64.getEncoder();
                    var encoded = new java.util.ArrayList<String>();
                    for (int index = 1; index < arguments.length; index++) {
                        encoded.add(encoder.encodeToString(arguments[index].getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    }
                    var input = new java.io.BufferedReader(new java.io.InputStreamReader(
                        System.in, java.nio.charset.StandardCharsets.UTF_8)).readLine();
                    java.nio.file.Files.write(java.nio.file.Path.of(arguments[0]),
                        java.util.List.of("arguments=" + String.join(",", encoded), "input=" + input),
                        java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        """.trimIndent())
        val javaExecutable = Path.of(System.getProperty("java.home"), "bin", if (SystemInfo.isWindows) "java.exe" else "java")
        val arguments = listOf("", "two words", "a'b", "a\"b", "a\\\"b", "trailing\\", "a\u2019b", "$&|;()!", "line1\nline2")
        val command = CliCommand(javaExecutable.toString(), listOf(source.toString(), result.toString()) + arguments, true)
        val runner = CliTerminalRunner(project, command)
        val options = runner.configureStartupOptions(ShellStartupOptions.Builder()
            .shellCommand(runner.tabState.myShellCommand)
            .workingDirectory(directory.toString())
            .build())
        val connector = if (useStartupOptions) runner.createTtyConnector(options) else runner.createTtyConnector(runner.createProcess(options))
        val process = requireNotNull(ShellTerminalWidget.getProcessTtyConnector(connector)).process
        try {
            process.outputStream.write("ready".toByteArray(Charsets.UTF_8))
            process.outputStream.write(TerminalKeyEncoder().getCode(KeyEvent.VK_ENTER, 0))
            process.outputStream.flush()
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue()
            process.inputStream.use { it.readAllBytes() }
            assertThat(process.exitValue()).isZero()
            val encodedArguments = arguments.joinToString(",") {
                Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
            }
            assertThat(Files.readAllLines(result)).containsExactly("arguments=$encodedArguments", "input=ready")
        } finally {
            connector.close()
        }
    }

    class StartupOptionsTerminalRunner(project: Project, private val start: (ShellStartupOptions) -> TtyConnector) : AbstractTerminalRunner<Process>(project) {
        fun createTtyConnector(startupOptions: ShellStartupOptions): TtyConnector = start(startupOptions)

        override fun createTtyConnector(process: Process): TtyConnector = error("Unexpected legacy connector call")
    }
}
