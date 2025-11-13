/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import java.awt.Point
import java.time.Duration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.findElement
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.openFile
import org.sonarlint.intellij.its.utils.optionalStep

@EnabledIf("isCLion")
class CLionTests : BaseUiTest() {

    @Test
    fun should_analyze_cpp() = uiTest {
        openExistingProject("sample-cpp")

        openFile("CMakeLists.txt")

        optionalStep {
            idea {
                actionHyperLink("Load CMake project") {
                    click()
                }
                // from 2021.1+
                dialog("Trust CMake Project?", Duration.ofSeconds(5)) {
                    button("Trust Project").click()
                }
                dialog("Open Project Wizard", Duration.ofSeconds(5)) {
                    button("OK").click()
                }
            }
        }

        optionalStep {
            idea {
                actionHyperLink("Fix\u2026") {
                    click()
                }
                findElement<ComponentFixture>(byXpath("//div[@class='MyList']")).click(Point(10, 10))
            }
        }

        idea {
            waitBackgroundTasksFinished()
        }

        openFile("main.cpp")

        verifyCurrentFileTabContainsMessages(
            "Found 4 issues",
            "array designators are a C99 extension",
            "Replace this macro by \"const\", \"constexpr\" or an \"enum\".",
            "Use \"std::array\" or \"std::vector\" instead of a C-style array.",
            "unused variable 's'"
        )
    }

}
