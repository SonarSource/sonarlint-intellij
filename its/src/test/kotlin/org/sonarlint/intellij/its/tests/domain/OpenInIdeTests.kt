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
import java.net.URL
import java.time.Duration
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.editor
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.jPasswordField
import org.sonarlint.intellij.its.fixtures.jbTextFields
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileRuleDescriptionTabContains
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.verifySecurityHotspotRuleDescriptionTabContains
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.verifySecurityHotspotTabContainsMessages
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.openInIde
class OpenInIdeTests {

    companion object {
        fun createConnection(token: String) {
            with(remoteRobot) {
                idea {
                    dialog("Trust This SonarQube Server Instance?") {
                        button("Connect to This SonarQube Server Instance").click()
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
                    dialog("Trust This SonarQube Server Instance?") {
                        jbTextFields()[1].text = "Orchestrator"
                        buttonContainsText("Connect to This SonarQube Server Instance").click()
                    }
                }
            }
        }

        fun acceptNewSCAutomatedConnection() {
            with(remoteRobot) {
                idea {
                    dialog("Trust This SonarQube Cloud Organization?") {
                        jbTextFields()[1].text = "sonarlint-it"
                        buttonContainsText("Connect to This SonarQube Cloud Organization").click()
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
            with(remoteRobot) {
                idea {
                    waitBackgroundTasksFinished()
                }
            }
            verifyCurrentFileTabContainsMessages("Remove this empty class, write its code or make it an \"interface\".")
            verifyCurrentFileRuleDescriptionTabContains("Classes should not be empty")
        }

        private fun verifyEditorOpened(fileName: String) {
            with(remoteRobot) {
                idea {
                    editor(fileName, Duration.ofMinutes(1))
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

        fun triggerOpenSCIssueRequest(
            projectKey: String,
            issueKey: String?,
            serverUrl: String,
            branch: String,
            tokenName: String,
            tokenValue: String,
            organizationKey: String
        ) {
            openInIde(projectKey, issueKey, serverUrl, branch, tokenName, tokenValue, organizationKey)
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
