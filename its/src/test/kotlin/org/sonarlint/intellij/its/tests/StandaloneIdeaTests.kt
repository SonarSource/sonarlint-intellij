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
package org.sonarlint.intellij.its.tests

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.ReportTabTests.Companion.analyzeAndVerifyReportTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.WalkthroughTests.Companion.verifyWalkthroughIsNotShowing
import org.sonarlint.intellij.its.tests.domain.WalkthroughTests.Companion.closeWalkthrough
import org.sonarlint.intellij.its.utils.ExclusionUtils.Companion.excludeFile
import org.sonarlint.intellij.its.utils.ExclusionUtils.Companion.removeFileExclusion
import org.sonarlint.intellij.its.utils.FiltersUtils.Companion.setFocusOnNewCode
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.closeProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile
import org.sonarlint.intellij.its.utils.SettingsUtils.Companion.toggleRule
import org.sonarlint.intellij.its.utils.optionalStep

@Tag("Standalone")
@EnabledIf("isIdeaCommunity")
class StandaloneIdeaTests : BaseUiTest() {

    @Test
    fun should_exclude_rule_and_focus_on_new_code() = uiTest {
        openExistingProject("sample-java-issues")
        openFile("src/main/java/foo/Foo.java", "Foo.java")
        toggleRule("java:S2094", "Classes should not be empty")
        verifyCurrentFileTabContainsMessages("No issues to display")
        toggleRule("java:S2094", "Classes should not be empty")
        setFocusOnNewCode()
        analyzeAndVerifyReportTabContainsMessages(
            "Found 1 new issue in 1 file from last 30 days",
            "No new Security Hotspots from last 30 days",
            "No older issues",
            "No older Security Hotspots"
        )
        verifyCurrentFileTabContainsMessages(
            "Found 1 new issue in 1 file from last 30 days",
            "No older issues",
        )
        verifyCurrentFileTabContainsMessages("Remove this empty class, write its code or make it an \"interface\".")
    }

    @Test
    fun should_exclude_file_and_analyze_file_and_no_issues_found() = uiTest {
        openExistingProject("sample-java-issues")
        excludeFile("src/main/java/foo/Foo.java")
        openFile("src/main/java/foo/Foo.java", "Foo.java")
        verifyCurrentFileTabContainsMessages("No analysis done on the current opened file")
        removeFileExclusion("src/main/java/foo/Foo.java")
    }

    @Test
    fun chart() = uiTest {
        openExistingProject("DuplicatedEnvsChart")
        openFile("templates/memory_limit_pod2.yml", "memory_limit_pod2.yml")
        verifyCurrentFileTabContainsMessages("Bind this resource's automounted service account to RBAC or disable automounting.")
    }

    @Test
    fun should_not_open_walkthrough_after_opened_once() = uiTest {
        openExistingProject("DuplicatedEnvsChart")
        optionalStep {
            closeWalkthrough()
        }
        closeProject()
        openExistingProject("sample-java-issues")
        verifyWalkthroughIsNotShowing()
    }
}
