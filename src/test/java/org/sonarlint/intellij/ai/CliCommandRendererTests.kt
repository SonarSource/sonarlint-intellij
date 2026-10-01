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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
            .isEqualTo("& '/path with spaces/sonar' 'a''b' '' '$&|;()!'")
    }

    @Test
    fun `doubles every PowerShell single quote character inside an argument`() {
        val connectionId = "Bob\u2019s server; Write-Output unexpected"
        val command = CliCommand("sonar", listOf("a'b\u2018c\u2019d\u201Ae\u201Bf", connectionId), true)
        val expected = "& 'sonar' 'a''b\u2018\u2018c\u2019\u2019d\u201A\u201Ae\u201B\u201Bf' " +
            "'Bob\u2019\u2019s server; Write-Output unexpected'"

        assertThat(CliCommandRenderer.render(command, CommandShell.POWERSHELL))
            .isEqualTo(expected)
    }
}
