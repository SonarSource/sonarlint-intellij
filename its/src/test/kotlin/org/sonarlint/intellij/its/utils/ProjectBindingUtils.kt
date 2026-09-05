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

    fun enableConnectedMode(projectKey: String, connectionName: String, connectionType: ConnectionType) {
        with(remoteRobot) {
            idea {
                dialog("Project Settings") {
                    val connectionLabelWithPrefix = connectionType.uiLabelPrefix + connectionName
                    checkBox("Bind project to SonarQube (Server, Cloud)").select()
                    comboBox("Connection:").click()
                    // a prefix is added only if there are multiple SQC connections
                    selectConnectionComboItem(connectionLabelWithPrefix, connectionName)
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
            selectConnectionComboItem("Orchestrator")
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

    // IC-2024.2 leaves other CustomComboPopup instances (run config, etc.). find() grabs the first.
    private fun selectConnectionComboItem(vararg names: String) {
        val expected = names.toList()
        waitFor(Duration.ofSeconds(10), errorMessage = "Connection combo did not contain any of $expected") {
            connectionComboPopup(expected) != null
        }
        val popup = requireNotNull(connectionComboPopup(expected))
        val label = expected.first { popup.hasText(it) }
        popup.findText(label).click()
    }

    private fun connectionComboPopup(names: List<String>): ContainerFixture? {
        return remoteRobot.findAll<ContainerFixture>(byXpath("//div[@class='CustomComboPopup']"))
            .firstOrNull { popup -> names.any { popup.hasText(it) } }
    }

}

enum class ConnectionType(val uiLabelPrefix: String) {
    SQS(""), SQC_EU("[EU] "), SQC_US("[US] ")
}
