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
import org.junit.jupiter.api.condition.DisabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests
import org.sonarlint.intellij.its.utils.OpeningUtils
import org.sonarlint.intellij.its.utils.SettingsUtils

@DisabledIf("isCLionOrGoLand")
class StandaloneIdeaTests : BaseUiTest() {

    @Test
    fun rule_exclusion() = uiTest {
        OpeningUtils.openExistingProject("sample-java-issues")
        OpeningUtils.openFile("src/main/java/foo/Foo.java", "Foo.java")
        SettingsUtils.toggleRule("java:S139", "Comments should not be located at the end of lines of code")
        CurrentFileTabTests.verifyCurrentFileTabContainsMessages("Move this trailing comment on the previous empty line.")
        SettingsUtils.toggleRule("java:S139", "Comments should not be located at the end of lines of code")
        CurrentFileTabTests.verifyCurrentFileTabContainsMessages("No issues to display")
    }

}
