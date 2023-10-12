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
package org.sonarlint.intellij.its.tests.domain

import com.intellij.remoterobot.RemoteRobot
import org.assertj.core.api.Assertions
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.notification
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.tests.AllUiTests.Companion.ORCHESTRATOR
import org.sonarlint.intellij.its.tests.AllUiTests.Companion.SECURITY_HOTSPOT_PROJECT_KEY
import org.sonarlint.intellij.its.tests.AllUiTests.Companion.token
import org.sonarlint.intellij.its.utils.ProjectBindingUtils

class SecurityHotspotTabTests {

    companion object {

        fun openSecurityHotspotReviewDialogFromList(remoteRobot: RemoteRobot, securityHotspotMessage: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
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

        fun bindProjectFromSecurityHotspotPanel(remoteRobot: RemoteRobot) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tab("Security Hotspots") { select() }
                        content("SecurityHotspotsPanel") {
                            findText("Configure Binding").click()
                        }
                    }
                    ProjectBindingUtils.bindProjectToSonarQube(
                        remoteRobot,
                        ORCHESTRATOR.server.url,
                        token,
                        SECURITY_HOTSPOT_PROJECT_KEY
                    )
                }
            }
        }

        fun changeSecurityHotspotStatusAndPressChange(remoteRobot: RemoteRobot, status: String) {
            with(remoteRobot) {
                idea {
                    dialog("Change Security Hotspot Status on SonarQube") {
                        content(status) {
                            click()
                        }
                        pressButton("Change Status")
                    }
                }
            }
        }

        fun verifySecurityHotspotStatusWasSuccessfullyChanged(remoteRobot: RemoteRobot) {
            with(remoteRobot) {
                idea {
                    notification("The Security Hotspot status was successfully updated")
                    toolWindow("SonarLint") {
                        content("SecurityHotspotsPanel") {
                            hasText("No Security Hotspot found.")
                        }
                    }
                }
            }
        }

        fun verifySecurityHotspotTabContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Security Hotspots") { select() }
                        content("SecurityHotspotsPanel") {
                            expectedMessages.forEach { Assertions.assertThat(hasText(it)).isTrue() }
                        }
                    }
                }
            }
        }

        fun verifySecurityHotspotTreeContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Security Hotspots") { select() }
                        content("SecurityHotspotTree") {
                            expectedMessages.forEach { Assertions.assertThat(hasText(it)).isTrue() }
                        }
                    }
                }
            }
        }
    }

}
