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
package org.sonarlint.intellij.ui.walkthrough

import com.intellij.ui.components.JBLabel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.fixtures.walkComponents
import org.sonarlint.intellij.ui.page.SonarLintUiTestContext
import org.sonarlint.intellij.ui.page.SonarLintWalkthroughToolWindowPage

class SonarLintWalkthroughToolWindowLightTests : AbstractSonarLintLightTests() {

    private lateinit var uiContext: SonarLintUiTestContext

    @BeforeEach
    fun setupUiContext() {
        uiContext = SonarLintUiTestContext(project)
    }

    @AfterEach
    fun teardown() {
        uiContext.releaseEditorsAndToolWindows()
    }

    @Test
    fun should_create_walkthrough_content_via_production_factory() {
        val walkthroughToolWindow = SonarLintWalkthroughToolWindowPage.create(project)
        val labelTexts = walkthroughToolWindow.content.walkComponents()
            .filterIsInstance<JBLabel>()
            .mapNotNull { it.text }
            .toList()
        assertThat(labelTexts).contains("Get started")
    }
}
