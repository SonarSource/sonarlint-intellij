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
 * License along with the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */
package org.sonarlint.intellij.its.tests.flavor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseStandaloneIntegrationTest
import org.sonarlint.intellij.its.driver.openProjectFile
import org.sonarlint.intellij.its.driver.verifyCurrentFileTabContainsMessages

@EnabledIf("isPhpStorm")
class PhpStormTests : BaseStandaloneIntegrationTest() {

    @Test
    fun should_analyze_php(testInfo: TestInfo) = uiTest(testInfo, "sample-php") {
        openProjectFile("file.php")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue",
            "Replace the \"var\" keyword with the modifier \"public\".",
        )
    }
}
