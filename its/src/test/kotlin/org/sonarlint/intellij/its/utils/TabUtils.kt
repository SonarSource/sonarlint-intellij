/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.its.utils

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.waitFor
import org.assertj.core.api.Assertions
import org.sonarlint.intellij.its.fixtures.closeAllGotItTooltips
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import java.time.Duration

class TabUtils {

    companion object {
        fun verifyCurrentFileTabContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Current File") { select() }
                        content("CurrentFilePanel") {
                            expectedMessages.forEach {
                                Assertions.assertThat(hasText(it)).`as`("Failed to find current file text '$it'").isTrue()
                            }
                        }
                    }
                }
            }
        }

        fun clickCurrentFileIssue(remoteRobot: RemoteRobot, issueMessage: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Current File") { select() }
                        closeAllGotItTooltips()
                        content("CurrentFilePanel") {
                            findText(issueMessage).click()
                        }
                    }
                }
            }
        }

        fun verifyRuleDescriptionTabContains(remoteRobot: RemoteRobot, expectedMessage: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        content("CurrentFilePanel") {
                            waitFor(Duration.ofSeconds(10), errorMessage = "Unable to find '$expectedMessage' in: ${findAllText()}") {
                                hasText(
                                    expectedMessage
                                )
                            }
                        }
                    }
                }
            }
        }

        fun verifyCurrentFileShowsCard(remoteRobot: RemoteRobot, expectedClass: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        Assertions.assertThat(findCard(expectedClass)).isNotNull
                    }
                }
            }
        }
    }

}
