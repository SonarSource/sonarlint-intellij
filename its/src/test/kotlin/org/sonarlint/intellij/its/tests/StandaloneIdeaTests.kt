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

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.ExclusionUtils.Companion.excludeFile
import org.sonarlint.intellij.its.utils.ExclusionUtils.Companion.removeFileExclusion
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile
import org.sonarlint.intellij.its.utils.SettingsUtils.Companion.toggleRule

@Tag("Standalone")
@EnabledIf("isIdeaCommunity")
class StandaloneIdeaTests : BaseUiTest() {

    @Test
    fun should_exclude_rule() = uiTest {
        openExistingProject("sample-java-issues")
        openFile("src/main/java/foo/Foo.java", "Foo.java")
        toggleRule("java:S2094", "Classes should not be empty")
        verifyCurrentFileTabContainsMessages("No issues to display")
        toggleRule("java:S2094", "Classes should not be empty")
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

}
