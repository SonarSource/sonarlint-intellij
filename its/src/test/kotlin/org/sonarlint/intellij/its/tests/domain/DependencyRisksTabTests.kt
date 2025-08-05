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
package org.sonarlint.intellij.its.tests.domain

import com.intellij.remoterobot.utils.waitFor
import java.time.Duration
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow

class DependencyRisksTabTests {

    companion object {

        fun verifyDependencyRisksTabContainsMessages(vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarQube for IDE") {
                        ensureOpen()
                        tabTitleContains("Dependency risks") { select() }
                        content("DependencyRiskPanel") {
                            // the synchronization can take a while to happen
                            expectedMessages.forEach {
                                waitFor(duration = Duration.ofSeconds(30)) {
                                    hasText(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
