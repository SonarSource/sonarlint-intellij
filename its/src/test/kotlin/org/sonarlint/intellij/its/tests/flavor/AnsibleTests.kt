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
package org.sonarlint.intellij.its.tests.flavor

import com.intellij.remoterobot.utils.waitFor
import com.sonar.orchestrator.container.Edition
import com.sonar.orchestrator.junit5.OrchestratorExtension
import com.sonar.orchestrator.locator.FileLocation
import java.time.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindowBar
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.enableConnectedModeFromCurrentFilePanel
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.openFile
import org.sonarlint.intellij.its.utils.OrchestratorUtils.defaultBuilderEnv
import org.sonarlint.intellij.its.utils.OrchestratorUtils.executeBuildWithSonarScanner
import org.sonarlint.intellij.its.utils.OrchestratorUtils.generateTokenNameAndValue
import org.sonarlint.intellij.its.utils.OrchestratorUtils.newAdminWsClientWithUser
import org.sonarlint.intellij.its.utils.SettingsUtils.clearConnectionsAndAddSonarQubeConnection

const val ANSIBLE_PROJECT_KEY = "sample-ansible"

@Tag("ConnectedAnalysisTests")
@EnabledIf("isIdeaCommunity")
class AnsibleTests : BaseUiTest() {

    @Test
    fun should_display_issue() = uiTest {
        openExistingProject("sample-ansible")
        openFile("HostNamespacesCheck/tasks/HostNamespacesCheck.yaml")
        verifyCurrentFileTabContainsMessages("No issues to display")

        enableConnectedModeFromCurrentFilePanel(ANSIBLE_PROJECT_KEY, true, "Orchestrator")

        idea {
            waitBackgroundTasksFinished()
        }

        verifyIssueTreeContainsMessages()
    }

    private fun verifyIssueTreeContainsMessages() {
        with(remoteRobot) {
            idea {
                toolWindowBar("SonarQube for IDE") {
                    ensureOpen()
                }
                toolWindow {
                    tabTitleContains("Current File") { select() }
                    // the synchronization can take a while to happen
                    waitFor(duration = Duration.ofMinutes(1)) {
                        hasText("Use a specific version tag for the image.")
                    }
                }
            }
        }
    }

    companion object {
        private val ORCHESTRATOR: OrchestratorExtension = defaultBuilderEnv()
            .setEdition(Edition.ENTERPRISE)
            .activateLicense()
            .keepBundledPlugins()
            .restoreProfileAtStartup(FileLocation.ofClasspath("/ansible-issue.xml"))
            .build()

        @JvmStatic
        @BeforeAll
        fun createSonarLintUser() {
            ORCHESTRATOR.start()

            val adminWsClient = newAdminWsClientWithUser(ORCHESTRATOR.server)
            val response = generateTokenNameAndValue(adminWsClient, "sonarlintUser")
            val token = response.second

            ORCHESTRATOR.server.provisionProject(ANSIBLE_PROJECT_KEY, "Sample Ansible Issues")
            ORCHESTRATOR.server.associateProjectToQualityProfile(
                ANSIBLE_PROJECT_KEY,
                "ansible",
                "SonarLint IT Ansible Issue"
            )

            // Build and analyze project to raise issue
            executeBuildWithSonarScanner("projects/sample-ansible/", ORCHESTRATOR, ANSIBLE_PROJECT_KEY)

            clearConnectionsAndAddSonarQubeConnection(ORCHESTRATOR.server.url, token)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            ORCHESTRATOR.stop()
        }
    }

}
