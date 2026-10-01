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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
}
