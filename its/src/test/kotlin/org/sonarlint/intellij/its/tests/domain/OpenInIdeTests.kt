/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.editor
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.jPasswordField
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileRuleDescriptionTabContains
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.verifySecurityHotspotRuleDescriptionTabContains
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.verifySecurityHotspotTabContainsMessages
import java.net.URL

class OpenInIdeTests {

    companion object {
        fun createConnection(token: String) {
            with(remoteRobot) {
                idea {
                    dialog("Do you trust this SonarQube server?") {
                        button("Connect to this SonarQube server").click()
                    }
                    dialog("New Connection: Server Details") {
                        keyboard { enterText("Orchestrator") }
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
                        pressCreate()
                    }
                }
            }
        }

        fun acceptNewAutomatedConnection() {
            with(remoteRobot) {
                idea {
                    dialog("Do You Trust This SonarQube Server?") {
                        button("Connect To This SonarQube Server").click()
                    }
                }
            }
        }

        fun verifyHotspotOpened() {
            verifyEditorOpened("Foo.java")
            verifySecurityHotspotTabContainsMessages("Make sure using this hardcoded IP address is safe here.")
            verifySecurityHotspotRuleDescriptionTabContains("What's the risk?")
        }

        fun verifyIssueOpened() {
            verifyEditorOpened("Bar.java")
            verifyCurrentFileTabContainsMessages("Move this trailing comment on the previous empty line.")
            verifyCurrentFileRuleDescriptionTabContains("Comments should not be located at the end of lines of code")
        }

        private fun verifyEditorOpened(fileName: String) {
            with(remoteRobot) {
                idea {
                    editor(fileName)
                }
            }
        }

        fun triggerOpenHotspotRequest(projectKey: String, firstHotspotKey: String?, serverUrl: String) {
            URL("http://localhost:64120/sonarlint/api/hotspots/show?project=$projectKey&hotspot=$firstHotspotKey&server=$serverUrl")
                .readText()
        }

        fun triggerOpenIssueRequest(
            projectKey: String,
            issueKey: String?,
            serverUrl: String,
            branch: String,
        ) {
            URL("http://localhost:64120/sonarlint/api/issues/show?project=$projectKey&issue=$issueKey&server=$serverUrl&branch=$branch")
                .readText()
        }

        fun triggerOpenIssueRequest(
            projectKey: String,
            issueKey: String?,
            serverUrl: String,
            branch: String,
            tokenName: String,
            tokenValue: String,
        ) {
            URL(
                "http://localhost:64120/sonarlint/api/issues/show" +
                    "?project=$projectKey&issue=$issueKey&server=$serverUrl&branch=$branch&tokenName=$tokenName&tokenValue=$tokenValue"
            ).readText()
        }
    }

}
