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

enum class CommandShell {
    POSIX,
    POWERSHELL
}

object CliCommandRenderer {
    private val POWERSHELL_SINGLE_QUOTES = Regex("['\u2018\u2019\u201A\u201B]")
    private val BACKSLASHES_BEFORE_DOUBLE_QUOTE = Regex("(\\\\*)\"")
    private val TRAILING_BACKSLASHES = Regex("(\\\\+)$")

    fun render(command: CliCommand, shell: CommandShell): String {
        val tokens = listOf(command.executable) + command.arguments
        return when (shell) {
            CommandShell.POSIX -> tokens.joinToString(" ") { quotePosix(it) }
            CommandShell.POWERSHELL -> renderPowerShell(command)
        }
    }

    private fun quotePosix(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    private fun quotePowerShell(value: String): String =
        "'" + value.replace(POWERSHELL_SINGLE_QUOTES) { it.value + it.value } + "'"

    private fun renderPowerShell(command: CliCommand): String {
        val executable = quotePowerShell(command.executable)
        val standardArguments = command.arguments.joinToString(" ") { quotePowerShell(it) }
        val legacyArguments = command.arguments.joinToString(" ") { quotePowerShell(quoteWindowsArgument(it)) }
        // Scope the preference to this invocation; older hosts need native command-line quoting.
        return "& { if (\$PSVersionTable.PSVersion -ge [version]'7.3') { " +
            "\$PSNativeCommandArgumentPassing = 'Standard'; & $executable $standardArguments " +
            "} else { & $executable $legacyArguments } }"
    }

    private fun quoteWindowsArgument(value: String): String =
        "\"" + value.replace(BACKSLASHES_BEFORE_DOUBLE_QUOTE) { it.groupValues[1].repeat(2) + "\\\"" }
            .replace(TRAILING_BACKSLASHES) { it.groupValues[1].repeat(2) } + "\""
}
