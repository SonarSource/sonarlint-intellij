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
package org.sonarlint.intellij.its.tests.flavor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFileViaMenu

@EnabledIf("isRider")
class RiderTests : BaseUiTest() {

    @Test
    fun should_analyze_csharp() = uiTest {
        openExistingProject("sample-rider")

        openFile("file.cs")

        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.cs",
            "Either remove or fill this block of code."
        )
    }

    @Test
    fun should_analyze_complex_csharp() = uiTest {
        openExistingProject("sample-complex-rider")

        openFile("folder1/file1.cs")
        //Check
        verifyCurrentFileTabContainsMessages(
            "Found 2 issues in 1 file",
            "file1.cs",
            "Remove this empty class, write its code or make it an \"interface\".",
            "Rename class 'file1' to match pascal case naming rules, consider using 'File1'."
        )

        openFileViaMenu("file2.cs")

        verifyCurrentFileTabContainsMessages(
            "Found 2 issues in 1 file",
            "file2.cs",
            "Remove this empty class, write its code or make it an \"interface\".",
            "Rename class 'file2' to match pascal case naming rules, consider using 'File2'."
        )
    }

}
