/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests

/** Light tests on [org.sonarlint.intellij.util.SonarLintAppUtils] */
class SonarLintAppUtilsLightTests : AbstractSonarLintLightTests() {
    /** SLI-833: Test case 1: Only one module */
    @Test
    fun test_getPathRelativeToModuleBaseDir_oneModuleContentRoot() {
        val directory = myFixture.copyDirectoryToProject("src", "src")
        assertThat(SonarLintAppUtils.getPathRelativeToModuleBaseDir(module, directory.findChild("Dummy.kt")!!))
            .isEqualTo("src/Dummy.kt")
    }

    /** SLI-833: Test case 2: Only one module but the requested file resides outside of module */
    @Test
    fun test_getPathRelativeToModuleBaseDir_oneModuleContentRoot_wrongFile() {
        val directory = myFixture.copyDirectoryToProject("src", "src")
        assertThat(SonarLintAppUtils.getPathRelativeToModuleBaseDir(module, directory.parent.parent))
            .isEqualTo(null)
    }
}
