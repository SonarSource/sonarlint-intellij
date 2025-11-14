/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.application
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintHeavyTests

/** Heavy tests on [org.sonarlint.intellij.util.SonarLintAppUtils] */
class SonarLintAppUtilsHeavyTests : AbstractSonarLintHeavyTests() {
    /** SLI-833: Test case 3: Only one module with multiple content roots */
    @Test
    fun test_getPathRelativeToModuleBaseDir_multipleModuleContentRoots() {
        lateinit var contentRoot1File: VirtualFile
        lateinit var contentRoot2File: VirtualFile

        val multiContentRootModule = createModule("SLI-833")

        val contentRoot1 = createTestProjectStructure()
        val contentRoot2 = createTestProjectStructure()
        ModuleRootModificationUtil.addContentRoot(multiContentRootModule, contentRoot1)
        ModuleRootModificationUtil.addContentRoot(multiContentRootModule, contentRoot2)

        application.runWriteAction {
            val contentRoot1Directory = contentRoot1.createChildDirectory(project, "src")
            contentRoot1File = contentRoot1Directory.createChildData(project, "Dummy.kt")

            val contentRoot2Directory = contentRoot2.createChildDirectory(project, "test")
            contentRoot2File = contentRoot2Directory.createChildData(project, "DummyTest.kt")
        }

        // Due to the content roots not being sorted we just test for both content roots
        assertThat(SonarLintAppUtils.getPathRelativeToModuleBaseDir(multiContentRootModule, contentRoot1File))
            .isEqualTo("src/Dummy.kt")
        assertThat(SonarLintAppUtils.getPathRelativeToModuleBaseDir(multiContentRootModule, contentRoot2File))
            .isEqualTo("test/DummyTest.kt")
    }
}
