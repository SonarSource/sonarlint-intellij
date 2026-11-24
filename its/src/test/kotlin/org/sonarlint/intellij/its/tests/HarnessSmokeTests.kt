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
package org.sonarlint.intellij.its.tests

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.isCLion
import org.sonarlint.intellij.its.fixtures.isRider
import org.sonarlint.intellij.its.fixtures.preferencesDialog
import org.sonarlint.intellij.its.utils.OpeningUtils.openExistingProject
import org.sonarlint.intellij.its.utils.SettingsUtils.settings

/** Exercises the UI harness without Orchestrator, server connections, or analysis assertions. */
@Tag("HarnessSmoke")
class HarnessSmokeTests : BaseUiTest() {

    @Test
    fun should_open_settings_and_project() = uiTest {
        // EAPs may start in a welcome project window; older IDEs use FlatWelcomeFrame.
        settings { pressCancel() }

        val projectName = when {
            isRider() -> "sample-rider"
            isCLion() -> "sample-cpp"
            else -> "sample-python"
        }
        openExistingProject(projectName)
        idea {
            // Finding an IDE frame alone can accidentally match the welcome project.
            val basePath = callJs<String>("component.project.getBasePath();", true)
            assertThat(File(basePath).canonicalPath).isEqualTo(File("projects/$projectName-tmp").canonicalPath)
            openSettings()
        }
        preferencesDialog { pressCancel() }
    }
}
