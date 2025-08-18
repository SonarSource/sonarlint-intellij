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

const val PLSQL_PROJECT_KEY = "sample-plsql"

@EnabledIf("isSQLPlugin")
class PLSQLTest : BaseUiTest() {

    @Test
    fun should_display_issue() = uiTest {
        openExistingProject("sample-plsql")
        openFile("file.pkb")
        verifyCurrentFileTabContainsMessages("No issues to display")

        enableConnectedModeFromCurrentFilePanel(PLSQL_PROJECT_KEY, true, "Orchestrator")

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
                        hasText("Remove this commented out code.")
                    }
                }
            }
        }
    }

    companion object {
        private val ORCHESTRATOR: OrchestratorExtension = defaultBuilderEnv()
            .setEdition(Edition.DEVELOPER)
            .activateLicense()
            .keepBundledPlugins()
            .restoreProfileAtStartup(FileLocation.ofClasspath("/plsql-issue.xml"))
            .build()

        @JvmStatic
        @BeforeAll
        fun createSonarLintUser() {
            ORCHESTRATOR.start()

            val adminWsClient = newAdminWsClientWithUser(ORCHESTRATOR.server)
            val response = generateTokenNameAndValue(adminWsClient, "sonarlintUser")
            val token = response.second

            ORCHESTRATOR.server.provisionProject(PLSQL_PROJECT_KEY, "Sample PLSQL Issues")
            ORCHESTRATOR.server.associateProjectToQualityProfile(
                PLSQL_PROJECT_KEY,
                "plsql",
                "SonarLint IT PLSQL Issue"
            )

            // Build and analyze project to raise issue
            executeBuildWithSonarScanner("projects/sample-plsql/", ORCHESTRATOR, PLSQL_PROJECT_KEY)

            clearConnectionsAndAddSonarQubeConnection(ORCHESTRATOR.server.url, token)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            ORCHESTRATOR.stop()
        }
    }

}
