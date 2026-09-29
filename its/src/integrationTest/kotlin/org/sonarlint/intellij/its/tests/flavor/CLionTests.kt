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
import org.sonarlint.intellij.its.driver.handleClionCppSetup
import org.sonarlint.intellij.its.driver.openProjectFile
import org.sonarlint.intellij.its.driver.verifyCurrentFileTabContainsMessages

@EnabledIf("isCLion")
class CLionTests : BaseStandaloneIntegrationTest() {

    @Test
    fun should_analyze_cpp(testInfo: TestInfo) = uiTest(testInfo, "sample-cpp") {
        openProjectFile("CMakeLists.txt")
        handleClionCppSetup()
        openProjectFile("main.cpp")
        verifyCurrentFileTabContainsMessages(
            "Found 4 issues",
            "array designators are a C99 extension",
            "Replace this macro by \"const\", \"constexpr\" or an \"enum\".",
            "Replace this C-style array with \"std::vector\" (for dynamic size), or \"std::array\" (for static size)",
            "unused variable 's'",
        )
    }

    @Test
    fun should_analyze_python(testInfo: TestInfo) = uiTest(testInfo, "sample-python") {
        openProjectFile("file.py")
        verifyCurrentFileTabContainsMessages(
            "Found 1 issue",
            "Refactor this method to not always return the same value.",
        )
    }
}
