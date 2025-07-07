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
import org.assertj.core.api.Assertions.assertThat
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.notification
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.utils.ProjectBindingUtils.disableConnectedMode
import org.sonarlint.intellij.its.utils.ProjectBindingUtils.enableConnectedMode

class SecurityHotspotTabTests {

    companion object {
        fun openSecurityHotspotReviewDialogFromList(securityHotspotMessage: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarQube for IDE") {
                        ensureOpen()
                        tabTitleContains("Security Hotspots") { select() }
                        content("SecurityHotspotTree") {
                            findText(securityHotspotMessage).rightClick()
                        }
                    }
                    actionMenuItem("Review Security Hotspot") {
                        click()
                    }
                }
            }
        }

        fun changeSecurityHotspotStatusAndPressChange(status: String) {
            with(remoteRobot) {
                idea {
                    dialog("Change Security Hotspot Status on SonarQube Server") {
                        content(status) {
                            click()
                        }
                        pressButton("Change Status")
                    }
                }
            }
        }

        fun verifySecurityHotspotStatusWasSuccessfullyChanged() {
            with(remoteRobot) {
                idea {
                    notification("The Security Hotspot status was successfully updated")
                    toolWindow("SonarQube for IDE") {
                        content("SecurityHotspotsPanel") {
                            hasText("No Security Hotspots shown due to the current filtering")
                        }
                    }
                }
            }
        }

        fun verifySecurityHotspotTabContainsMessages(vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarQube for IDE") {
                        ensureOpen()
                        tabTitleContains("Security Hotspots") { select() }
                        content("SecurityHotspotsPanel") {
                            expectedMessages.forEach { assertThat(hasText(it)).isTrue() }
                        }
                    }
                }
            }
        }

        fun verifySecurityHotspotTreeContainsMessages(vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarQube for IDE") {
                        ensureOpen()
                        tabTitleContains("Security Hotspots") { select() }
                        content("SecurityHotspotTree") {
                            expectedMessages.forEach { assertThat(hasText(it)).isTrue() }
                        }
                    }
                }
            }
        }

        fun enableConnectedModeFromSecurityHotspotPanel(projectKey: String, enabled: Boolean, connectionName: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarQube for IDE") {
                        ensureOpen()
                        tabTitleContains("Security Hotspots") { select() }
                        content("SecurityHotspotsPanel") {
                            toolBarButton("Configure SonarQube for IDE").click()
                        }
                    }
                    if (enabled) {
                        enableConnectedMode(projectKey, connectionName)
                    } else {
                        disableConnectedMode()
                    }
                }
            }
        }

        fun verifySecurityHotspotRuleDescriptionTabContains(expectedMessage: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarQube for IDE") {
                        ensureOpen()
                        content("SecurityHotspotsPanel") {
                            waitFor(Duration.ofMinutes(1), errorMessage = "Unable to find '$expectedMessage' in: ${findAllText()}") {
                                hasText(expectedMessage)
                            }
                        }
                    }
                }
            }
        }
    }

}
