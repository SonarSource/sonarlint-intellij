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
package org.sonarlint.intellij.its.utils

import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.JButtonFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.clickWhenEnabled
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.isRider
import org.sonarlint.intellij.its.fixtures.jbTable
import org.sonarlint.intellij.its.fixtures.jbTextField
import org.sonarlint.intellij.its.utils.SettingsUtils.sonarLintGlobalSettings

object ProjectBindingUtils {

    fun disableConnectedMode() {
        with(remoteRobot) {
            idea {
                dialog("Project Settings") {
                    checkBox("Bind project to SonarQube (Server, Cloud)").unselect()
                    if (isRider()) {
                        button("Save").click()
                    } else {
                        button("OK").click()
                    }
                }
            }
        }
    }

    fun enableConnectedMode(projectKey: String, connectionName: String) {
        with(remoteRobot) {
            idea {
                dialog("Project Settings") {
                    checkBox("Bind project to SonarQube (Server, Cloud)").select()
                    comboBox("Connection:").click()
                    remoteRobot.find<ContainerFixture>(byXpath("//div[@class='CustomComboPopup']")).apply {
                        waitFor(Duration.ofSeconds(5)) { hasText(connectionName) }
                        findText(connectionName).click()
                    }
                    jbTextField().text = projectKey
                    button("OK").click()
                    // wait for binding fully established
                    waitFor(Duration.ofSeconds(25)) { !isShowing }
                }
            }
        }
    }

    fun bindProjectAndModuleInFileSettings(moduleName: String, projectKey: String, moduleProjectKey: String) {
        sonarLintGlobalSettings {
            tree {
                clickPath("Tools", "SonarQube for IDE", "Project Settings")
            }
            checkBox("Bind project to SonarQube (Server, Cloud)").select()
            pressOk()
            errorMessage("Connection should not be empty")

            comboBox("Connection:").click()
            remoteRobot.find<ContainerFixture>(byXpath("//div[@class='CustomComboPopup']")).apply {
                waitFor(Duration.ofSeconds(5)) { hasText("Orchestrator") }
                findText("Orchestrator").click()
            }
            pressOk()
            errorMessage("Project key should not be empty")

            jbTextField().text = projectKey

            addConnectionButton().clickWhenEnabled()
            dialog("Select module") {
                jbTable().selectItemContaining(moduleName)
                pressOk()
            }

            pressOk()
            errorMessage("Project key for module '$moduleName' should not be empty")
            buttons(JButtonFixture.byText("Search in list\u2026"))[1].click()
            dialog("Select SonarQube Server Project To Bind") {
                jList {
                    clickItem(moduleProjectKey, false)
                }
                pressOk()
            }
            pressOk()
        }
    }

}
