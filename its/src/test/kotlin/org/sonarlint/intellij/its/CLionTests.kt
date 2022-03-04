/*
 * SonarLint for IntelliJ IDEA ITs
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.its

import com.intellij.remoterobot.RemoteRobot
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.isCLion
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.utils.optionalStep
import java.time.Duration


class CLionTests : BaseUiTest() {

  @BeforeEach
  fun requirements() {
    Assume.assumeTrue(remoteRobot.isCLion())
  }

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

    idea {
      waitBackgroundTasksFinished()
    }

    openFile("main.cpp")

    verifyCurrentFileTabContainsMessages(
      this,
      "Found 5 issues in 1 file",
      "main.cpp",
      "array designators are a C99 extension",
      "Replace this macro by \"const\", \"constexpr\" or an \"enum\".",
      "Replace this usage of \"std::cout\" by a logger.",
      "Use \"std::array\" or \"std::vector\" instead of a C-style array.",
      "unused variable 's'"
    )
  }

  private fun verifyCurrentFileTabContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
    with(remoteRobot) {
      idea {
        toolWindow("SonarLint") {
          ensureOpen()
          tabTitleContains("Current file") { select() }
          content("SonarLintIssuesPanel") {
            expectedMessages.forEach { assertThat(hasText(it)).isTrue() }
          }
        }
      }
    }
  }

}
