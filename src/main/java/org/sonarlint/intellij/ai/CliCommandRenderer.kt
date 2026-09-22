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
    fun render(command: CliCommand, shell: CommandShell): String {
        val tokens = listOf(command.executable) + command.arguments
        return when (shell) {
            CommandShell.POSIX -> tokens.joinToString(" ") { quotePosix(it) }
            CommandShell.POWERSHELL -> "& " + tokens.joinToString(" ") { quotePowerShell(it) }
        }
    }

    private fun quotePosix(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

    private fun quotePowerShell(value: String): String = "'${value.replace("'", "''")}'"
}
