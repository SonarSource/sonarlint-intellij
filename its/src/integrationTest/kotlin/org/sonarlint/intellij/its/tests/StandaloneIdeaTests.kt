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
package org.sonarlint.intellij.its.tests

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseStandaloneIntegrationTest
import org.sonarlint.intellij.its.driver.analyzeAndVerifyReportTabContainsMessages
import org.sonarlint.intellij.its.driver.analyzeCurrentFileFromToolWindow
import org.sonarlint.intellij.its.driver.closeProject
import org.sonarlint.intellij.its.driver.closeWalkthrough
import org.sonarlint.intellij.its.driver.excludeFile
import org.sonarlint.intellij.its.driver.openExistingProject
import org.sonarlint.intellij.its.driver.openProjectFile
import org.sonarlint.intellij.its.driver.removeFileExclusion
import org.sonarlint.intellij.its.driver.resetFocusOnNewCode
import org.sonarlint.intellij.its.driver.setFocusOnNewCode
import org.sonarlint.intellij.its.driver.toggleRule
import org.sonarlint.intellij.its.driver.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.driver.verifyWalkthroughIsNotShowing

@Tag("Standalone")
@EnabledIf("isIdeaCommunity")
class StandaloneIdeaTests : BaseStandaloneIntegrationTest() {

    @Test
    fun should_exclude_rule_and_focus_on_new_code(testInfo: TestInfo) = uiTest(testInfo, "sli-java-issues") {
        openProjectFile("src/main/java/foo/Foo.java")
        toggleRule("java:S2094", "Classes should not be empty")
        verifyCurrentFileTabContainsMessages("No findings to display")
        toggleRule("java:S2094", "Classes should not be empty")
        setFocusOnNewCode()
        analyzeAndVerifyReportTabContainsMessages(
            "Found 1 new issue from last 30 days",
            "No new Security Hotspots from last 30 days",
            "No older issues",
            "No older Security Hotspots",
        )
        analyzeCurrentFileFromToolWindow()
        verifyCurrentFileTabContainsMessages(
            "Found 1 new issue from last 30 days",
        )
        verifyCurrentFileTabContainsMessages("Remove this empty class, write its code or make it an \"interface\".")
        resetFocusOnNewCode()
    }

    @Test
    fun should_exclude_file_and_analyze_file_and_no_issues_found(testInfo: TestInfo) = uiTest(testInfo, "sli-java-issues") {
        excludeFile("src/main/java/foo/Bar.java")
        openProjectFile("src/main/java/foo/Bar.java")
        verifyCurrentFileTabContainsMessages("No findings to display")
        removeFileExclusion("src/main/java/foo/Bar.java")
    }

    @Test
    fun should_analyze_ansible(testInfo: TestInfo) = uiTest(testInfo, "DuplicatedEnvsChart") {
        openProjectFile("templates/memory_limit_pod2.yml")
        verifyCurrentFileTabContainsMessages("Bind this resource's automounted service account to RBAC or disable automounting.")
    }

    @Test
    fun should_not_open_walkthrough_after_opened_once(testInfo: TestInfo) = uiTest(testInfo, "DuplicatedEnvsChart") {
        runCatching { closeWalkthrough() }
        closeProject()
        openExistingProject("sli-java-issues")
        verifyWalkthroughIsNotShowing()
    }
}
