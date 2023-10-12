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
import com.intellij.remoterobot.utils.keyboard
import org.assertj.core.api.Assertions
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.editor
import org.sonarlint.intellij.its.fixtures.fileBrowserDialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.jPasswordField
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.tests.AllUiTests
import org.sonarlint.intellij.its.utils.optionalStep
import java.net.URL

class OpenInIdeTests {

    companion object {
        fun createConnection(robot: RemoteRobot) {
            with(robot) {
                idea {
                    dialog("Opening Security Hotspot...") {
                        button("Create connection").click()
                    }
                    dialog("New Connection: Server Details") {
                        keyboard { enterText("Orchestrator") }
                        button("Next").click()
                    }
                    dialog("New Connection: Authentication") {
                        jPasswordField().text = AllUiTests.token
                        button("Next").click()
                    }
                    dialog("New Connection: Configure Notifications") {
                        button("Next").click()
                    }
                    dialog("New Connection: Configuration completed") {
                        pressFinishOrCreate()
                    }
                }
            }
        }

        fun bindRecentProject(robot: RemoteRobot) {
            with(robot) {
                idea {
                    dialog("Opening Security Hotspot...") {
                        button("Select project").click()
                    }
                    dialog("Select a project") {
                        button("Open or import").click()
                    }
                    fileBrowserDialog(arrayOf("Select Path")) {
                        selectProjectFile("sample-java-hotspot", true)
                    }
                    optionalStep {
                        dialog("Open Project") {
                            button("This Window").click()
                        }
                    }
                    dialog("Opening Security Hotspot...") {
                        button("Yes").click()
                    }
                }
            }
        }

        fun verifyHotspotOpened(robot: RemoteRobot) {
            verifyEditorOpened(robot)
            verifyToolWindowFilled(robot)
        }

        fun verifyEditorOpened(robot: RemoteRobot) {
            with(robot) {
                idea {
                    editor("Foo.java")
                }
            }
        }

        fun verifyToolWindowFilled(robot: RemoteRobot) {
            with(robot) {
                idea {
                    toolWindow("SonarLint") {
                        tabTitleContains("Security Hotspots") {
                            content("SecurityHotspotsPanel") {
                                Assertions.assertThat(hasText("Make sure using this hardcoded IP address is safe here.")).isTrue()
                            }
                        }
                    }
                }
            }
        }

        fun triggerOpenHotspotRequest(projectKey: String, firstHotspotKey: String?, serverUrl: String) {
            URL("http://localhost:64120/sonarlint/api/hotspots/show?project=$projectKey&hotspot=$firstHotspotKey&server=$serverUrl")
                .readText()
        }
    }

}
