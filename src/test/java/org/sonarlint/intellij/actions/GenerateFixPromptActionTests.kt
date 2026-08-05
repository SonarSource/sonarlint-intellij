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
package org.sonarlint.intellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.sonarlint.intellij.AbstractSonarLintLightTests

class GenerateFixPromptActionTests : AbstractSonarLintLightTests() {

    private lateinit var action: GenerateFixPromptAction
    private lateinit var mockEvent: AnActionEvent
    private lateinit var presentation: Presentation

    @BeforeEach
    fun init() {
        action = GenerateFixPromptAction()
        presentation = Presentation()
        mockEvent = mock(AnActionEvent::class.java)
        `when`(mockEvent.presentation).thenReturn(presentation)
    }

    @Test
    fun `update disables action when project is null`() {
        `when`(mockEvent.project).thenReturn(null)

        action.update(mockEvent)

        assertThat(presentation.isEnabled).isFalse()
    }
}
