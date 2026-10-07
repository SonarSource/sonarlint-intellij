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
package org.sonarlint.intellij.its.tests.flavor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseStandaloneIntegrationTest
import org.sonarlint.intellij.its.driver.analyzeCurrentFileFromToolWindow
import org.sonarlint.intellij.its.driver.openProjectFile
import org.sonarlint.intellij.its.driver.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.driver.waitForBackgroundTasksFinished

@EnabledIf("isGoPlugin")
class GoPluginTests : BaseStandaloneIntegrationTest() {

    @Test
    fun should_analyze_go(testInfo: TestInfo) = uiTest(testInfo, "sample-go") {
        openProjectFile("file.go")
        // On IU the Go SDK download/indexing can finish after the file is opened; re-analyze explicitly.
        waitForBackgroundTasksFinished()
        analyzeCurrentFileFromToolWindow()
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue",
            "Remove or correct this useless self-assignment.",
        )
    }
}
