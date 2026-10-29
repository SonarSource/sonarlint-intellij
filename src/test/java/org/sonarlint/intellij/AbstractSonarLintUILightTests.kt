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
package org.sonarlint.intellij

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.sonarlint.intellij.ui.page.SonarLintMainToolWindowPage
import org.sonarlint.intellij.ui.page.SonarLintUiTestContext

abstract class AbstractSonarLintUILightTests : AbstractSonarLintLightTests() {

    protected lateinit var mainToolWindow: SonarLintMainToolWindowPage
    private lateinit var uiContext: SonarLintUiTestContext

    @BeforeEach
    fun openMainToolWindow() {
        uiContext = SonarLintUiTestContext(project)
        mainToolWindow = SonarLintMainToolWindowPage.create(project)
    }

    @AfterEach
    fun closeMainToolWindow() {
        uiContext.closeOpenEditorsAndSonarLintToolWindows()
    }
}
