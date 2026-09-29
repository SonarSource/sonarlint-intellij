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
import org.sonarlint.intellij.its.driver.openFileViaMenu
import org.sonarlint.intellij.its.driver.openProjectFile
import org.sonarlint.intellij.its.driver.verifyCurrentFileTabContainsMessages

@EnabledIf("isRider")
class RiderTests : BaseStandaloneIntegrationTest() {

    @Test
    fun should_analyze_csharp(testInfo: TestInfo) = uiTest(testInfo, "sample-rider") {
        openProjectFile("file.cs")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue",
            "Either remove or fill this block of code.",
        )
    }

    @Test
    fun should_analyze_complex_csharp(testInfo: TestInfo) = uiTest(testInfo, "sample-complex-rider") {
        openProjectFile("folder1/file1.cs")
        verifyCurrentFileTabContainsMessages(
            "Found 2 issues",
            "Remove this empty class, write its code or make it an \"interface\".",
            "Rename class 'file1' to match pascal case naming rules, consider using 'File1'.",
        )
        openFileViaMenu("file2.cs")
        verifyCurrentFileTabContainsMessages(
            "Found 2 issues",
            "Remove this empty class, write its code or make it an \"interface\".",
            "Rename class 'file2' to match pascal case naming rules, consider using 'File2'.",
        )
    }
}
