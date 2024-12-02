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
package org.sonarlint.intellij.its.tests.flavor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile


@EnabledIf("isIdeaUltimate")
class IdeaUltimateTests : BaseUiTest() {

    @Test
    fun should_analyze_iac() = uiTest {
        openExistingProject("sample-iac")

        openFile("file.yaml")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.yaml",
            "Rename tag key \"anycompany:cost-center\" to match the regular expression \"^([A-Z][A-Za-z]*:)*([A-Z][A-Za-z]*)\$\"."
        )

        openFile("Dockerfile")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "Dockerfile",
            "Replace `from` with upper case format `FROM`."
        )

        openFile("kubernetes.yaml")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "kubernetes.yaml",
            "Replace this wildcard with a clear list of allowed resources."
        )

        openFile("file.tf")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.tf",
            "Rename tag key \"anycompany:cost-center\" to match the regular expression \"^([A-Z][A-Za-z]*:)*([A-Z][A-Za-z]*)\$\"."
        )
    }

    @Test
    fun should_analyze_kotlin() = uiTest {
        openExistingProject("sample-kotlin")

        openFile("file.kt")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.kt",
            "Make this interface functional or replace it with a function type."
        )
    }

    @Test
    fun should_analyze_xml() = uiTest {
        openExistingProject("sample-xml")

        openFile("file.xml")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "file.xml",
            "Take the required action to fix the issue indicated by this \"FIXME\" comment."
        )
    }

    @EnabledIf("isWebStorm")
    @Test
    fun should_analyze_js_ts_css_html() = uiTest {
        openExistingProject("sample-js-ts-css-html")

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
