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

import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CliCommandRendererTests {
    private val command = CliCommand("/path with spaces/sonar", listOf("a'b", "", "$&|;()!"), true)

    @Test
    fun `quotes every token for POSIX shells`() {
        assertThat(CliCommandRenderer.render(command, CommandShell.POSIX))
            .isEqualTo("'/path with spaces/sonar' 'a'\"'\"'b' '' '$&|;()!'")
    }

    @Test
    fun `quotes every token for PowerShell`() {
        assertThat(CliCommandRenderer.render(command, CommandShell.POWERSHELL))
            .contains("\$PSNativeCommandArgumentPassing = 'Standard'")
            .contains("& '/path with spaces/sonar' 'a''b' '' '$&|;()!'")
            .contains("& '/path with spaces/sonar' '\"a''b\"' '\"\"' '\"$&|;()!\"'")
    }

    @Test
    fun `doubles every PowerShell single quote character inside an argument`() {
        val connectionId = "Bob\u2019s server; Write-Output unexpected"
        val command = CliCommand("sonar", listOf("a'b\u2018c\u2019d\u201Ae\u201Bf", connectionId), true)
        val expected = "& 'sonar' 'a''b\u2018\u2018c\u2019\u2019d\u201A\u201Ae\u201B\u201Bf' " +
            "'Bob\u2019\u2019s server; Write-Output unexpected'"

        assertThat(CliCommandRenderer.render(command, CommandShell.POWERSHELL))
            .contains(expected)
    }

    @Test
    fun powershell_preserves_native_argv_and_restores_argument_passing_preference(@TempDir directory: Path) {
        val powerShell = System.getenv("TEST_POWERSHELL_EXECUTABLE")
            ?: if (SystemInfo.isWindows) "powershell.exe" else null
        assumeTrue(powerShell != null, "Requires PowerShell; set TEST_POWERSHELL_EXECUTABLE on non-Windows hosts")
        val javaExecutable = Path.of(System.getProperty("java.home"), "bin", if (SystemInfo.isWindows) "java.exe" else "java").toString()
        val source = Files.createDirectories(directory.resolve("path with spaces")).resolve("ArgvProbe.java")
        Files.writeString(source, """
            class ArgvProbe {
                public static void main(String[] arguments) {
                    for (String argument : arguments) {
                        System.out.println(java.util.Base64.getEncoder().encodeToString(
                            argument.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    }
                }
            }
        """.trimIndent())
        val arguments = listOf(
            "", " ", "a\"b", "a\\\"b", "two words", "two words\\", "two words\\\\",
            "C:\\path with spaces\\", "line1\nline2", "a'b\u2018c\u2019d\u201Ae\u201Bf", "$&|;()!"
        )
        val command = CliCommand(javaExecutable, listOf(source.toString()) + arguments, true)
        val rendered = CliCommandRenderer.render(command, CommandShell.POWERSHELL)

        listOf("Legacy", "Windows", "Standard").forEach { mode ->
            val script = "\$PSNativeCommandArgumentPassing = '$mode'; $rendered; " +
                "if (\$PSNativeCommandArgumentPassing -ne '$mode') { throw 'Argument-passing preference leaked' }"
            val process = ProcessBuilder(requireNotNull(powerShell), "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).start()
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue()
            val output = process.inputStream.bufferedReader().readLines()
            assertThat(process.exitValue()).withFailMessage(output.joinToString("\n")).isZero()
            assertThat(output).containsExactlyElementsOf(arguments.map {
                Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
            })
        }
    }

}
