/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile

@EnabledIf("isWebStorm")
class WebStormTests : BaseUiTest() {

    @Test
    fun should_analyze_js_ts_css_html() = uiTest {
        openExistingProject("sample-js-ts")

        openFile("file.js")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.js",
            "Unexpected comma in middle of array."
        )

        openFile("file2.ts")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file2.ts",
            "Unexpected var, use let or const instead."
        )

        openFile("file3.css")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file3.css",
            "Unexpected empty block"
        )

        openFile("file4.html")
        verifyCurrentFileTabContainsMessages(
            "Found 2 issues in 1 file",
            "file4.html",
            "\"tabIndex\" should only be declared on interactive elements.",
            "Avoid using positive values for the \"tabIndex\" attribute."
        )
    }

}
