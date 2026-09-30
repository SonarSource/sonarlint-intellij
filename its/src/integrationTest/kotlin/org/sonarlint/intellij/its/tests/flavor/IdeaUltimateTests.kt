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
import org.sonarlint.intellij.its.driver.openProjectFile
import org.sonarlint.intellij.its.driver.verifyCurrentFileTabContainsMessages

@EnabledIf("isIdeaUltimate")
class IdeaUltimateTests : BaseStandaloneIntegrationTest() {

    @Test
    fun should_analyze_iac(testInfo: TestInfo) = uiTest(testInfo, "sample-iac") {
        openProjectFile("file.yaml")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue",
            "Change this code to disable support of older TLS versions.",
        )
        openProjectFile("Dockerfile")
        verifyCurrentFileTabContainsMessages(
            "Found 2 issues",
            "The \"ubuntu\" image runs with \"root\" as the default user. Make sure it is safe here.",
            "Replace \"from\" with upper case format \"FROM\".",
        )
        openProjectFile("kubernetes.yaml")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue",
            "Replace this wildcard with a clear list of allowed resources.",
        )
        openProjectFile("file.tf")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue",
            "Change this code to disable support of older TLS versions.",
        )
    }

    @Test
    fun should_analyze_kotlin(testInfo: TestInfo) = uiTest(testInfo, "sample-kotlin") {
        openProjectFile("file.kt")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue",
            "Make this interface functional or replace it with a function type.",
        )
    }

    @Test
    fun should_analyze_xml(testInfo: TestInfo) = uiTest(testInfo, "sample-xml") {
        openProjectFile("file.xml")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue",
            "Take the required action to fix the issue indicated by this \"FIXME\" comment.",
        )
    }

    @EnabledIf("isWebStorm")
    @Test
    fun should_analyze_js_ts_css_html(testInfo: TestInfo) = uiTest(testInfo, "sample-js-ts-css-html") {
        openProjectFile("file.js")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue",
            "Correct one of the identical sub-expressions on both sides of operator \"&&\"",
        )
        openProjectFile("file2.ts")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue",
            "Unexpected var, use let or const instead.",
        )
        openProjectFile("file3.css")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue",
            "Empty block",
        )
        openProjectFile("file4.html")
        verifyCurrentFileTabContainsMessages(
            "Found 2 issues",
            "\"tabIndex\" should only be declared on interactive elements.",
            "Avoid using positive values for the \"tabIndex\" attribute.",
        )
    }
}
