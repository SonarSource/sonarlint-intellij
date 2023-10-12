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
import com.intellij.remoterobot.fixtures.ActionButtonFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.JButtonFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.clickWhenEnabled
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.jPasswordField
import org.sonarlint.intellij.its.fixtures.jRadioButtons
import org.sonarlint.intellij.its.fixtures.jbTable
import org.sonarlint.intellij.its.fixtures.jbTextField
import org.sonarlint.intellij.its.fixtures.jbTextFields
import org.sonarlint.intellij.its.tests.AllUiTests
import java.time.Duration

class ProjectBindingUtils {

    companion object {
        fun bindProjectToSonarQube(remoteRobot: RemoteRobot, serverUrl: String, token: String, projectKey: String) {
            with(remoteRobot) {
                idea {
                    dialog("Project Settings") {
                        checkBox("Bind project to SonarQube / SonarCloud").select()
                        button("Configure the connection...").click()
                        dialog("SonarLint") {
                            actionButton(ActionButtonFixture.byTooltipText("Add")).clickWhenEnabled()
                            dialog("New Connection: Server Details") {
                                keyboard { enterText("Orchestrator") }
                                jRadioButtons()[1].select()
                                jbTextFields()[1].text = serverUrl
                                button("Next").click()
                            }
                            dialog("New Connection: Authentication") {
                                jPasswordField().text = token
                                button("Next").click()
                            }
                            dialog("New Connection: Configure Notifications") {
                                button("Next").click()
                            }
                            dialog("New Connection: Configuration completed") {
                                pressFinishOrCreate()
                            }
                            button("OK").click()
                        }
                        comboBox("Connection:").click()
                        remoteRobot.find<ContainerFixture>(byXpath("//div[@class='CustomComboPopup']")).apply {
                            waitFor(Duration.ofSeconds(5)) { hasText("Orchestrator") }
                            findText("Orchestrator").click()
                        }
                        jbTextField().text = projectKey
                        button("OK").click()
                        // wait for binding fully established
                        waitFor(Duration.ofSeconds(20)) { !isShowing }
                    }
                }
            }
        }

        fun unbindProjectToSonarQube(remoteRobot: RemoteRobot) {
            with(remoteRobot) {
                idea {
                    dialog("Project Settings") {
                        checkBox("Bind project to SonarQube / SonarCloud").select()
                        button("Configure the connection...").click()
                        dialog("SonarLint") {
                            actionButton(ActionButtonFixture.byTooltipText("Remove")).clickWhenEnabled()
                            button("OK").click()
                        }
                        button("OK").click()
                    }
                }
            }
        }

        fun bindProjectAndModuleInFileSettings() {
            SettingsUtils.sonarLintGlobalSettings(BaseUiTest.remoteRobot) {
                actionButton(ActionButtonFixture.byTooltipText("Add")).clickWhenEnabled()
                dialog("New Connection: Server Details") {
                    keyboard { enterText("Orchestrator") }
                    jRadioButtons()[1].select()
                    jbTextFields()[1].text = AllUiTests.ORCHESTRATOR.server.url
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
                tree {
                    clickPath("Tools", "SonarLint", "Project Settings")
                }
                checkBox("Bind project to SonarQube / SonarCloud").select()
                pressOk()
                errorMessage("Connection should not be empty")

                comboBox("Connection:").click()
                remoteRobot.find<ContainerFixture>(byXpath("//div[@class='CustomComboPopup']")).apply {
                    waitFor(Duration.ofSeconds(5)) { hasText("Orchestrator") }
                    findText("Orchestrator").click()
                }
                pressOk()
                errorMessage("Project key should not be empty")

                jbTextField().text = AllUiTests.PROJECT_KEY

                actionButton(ActionButtonFixture.byTooltipText("Add")).clickWhenEnabled()
                dialog("Select module") {
                    jbTable().selectItemContaining("sample-scala-module")
                    pressOk()
                }

                pressOk()
                errorMessage("Project key for module 'sample-scala-module' should not be empty")
                buttons(JButtonFixture.byText("Search in list..."))[1].click()
                dialog("Select SonarQube Project To Bind") {
                    jList {
                        clickItem(AllUiTests.MODULE_PROJECT_KEY, false)
                    }
                    pressOk()
                }
                pressOk()
            }
        }
    }

}
