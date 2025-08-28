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

import com.intellij.remoterobot.utils.keyboard
import org.assertj.core.api.Assertions.assertThat
import org.sonarlint.intellij.its.fixtures.clickWhenEnabled
import org.sonarlint.intellij.its.fixtures.configureConnectionDialog
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.jPasswordField
import org.sonarlint.intellij.its.fixtures.jbTextField
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.utils.SettingsUtils.optionalIdeaFrame

object ConnectionFromToolWindowTests {

    fun bindProjectFromToolWindow(projectKey: String, connectionName: String, token: String, expectedInitialConnection: String) {
        optionalIdeaFrame()?.apply {
            toolWindow {
                tabTitleContains("Current File") { select() }
                content("CurrentFilePanel") {
                    toolBarButton("Configure SonarQube for IDE").click()
                }
            }

            dialog("Project Settings") {
                checkBox("Bind project to SonarQube (Server, Cloud)").select()
                comboBox("Connection:").hasText(expectedInitialConnection)
                button("Configure the connection…").click()

                configureConnectionDialog {
                    addConnectionButton().clickWhenEnabled()
                    dialog("New Connection: Server Details") {
                        keyboard { enterText(connectionName) }
                        button("Next").click()
                    }
                    dialog("New Connection: Authentication") {
                        jPasswordField().text = token
                        button("Next").click()
                    }
                    dialog("New Connection: Organization") {
                        button("Next").click()
                    }
                    dialog("New Connection: Configure Notifications") {
                        button("Next").click()
                    }
                    dialog("New Connection: Configuration completed") {
                        pressCreate()
                    }
                    jList {
                        hasText(connectionName)
                    }
                    pressOk()
                }

                assertThat(comboBox("Connection:").extractData().first().text).contains(connectionName)
                jbTextField().hasText("Input SonarQube (Server, Cloud) project key or search one")

                jbTextField().text = projectKey

                button("OK").click()
            }
        }
    }
}
