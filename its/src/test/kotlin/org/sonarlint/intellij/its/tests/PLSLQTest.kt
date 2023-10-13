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
package org.sonarlint.intellij.its.tests

import com.intellij.remoterobot.RemoteRobot
import com.sonar.orchestrator.container.Edition
import com.sonar.orchestrator.junit5.OrchestratorExtension
import com.sonar.orchestrator.locator.FileLocation
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.enableConnectedModeFromCurrentFilePanel
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile
import org.sonarlint.intellij.its.utils.OrchestratorUtils

const val PLSQL_PROJECT_KEY = "sample-plsql"

@EnabledIf("isSQLPlugin")
class PLSQLTest : BaseUiTest() {

    @Test
    fun should_display_issue() = uiTest {
        openExistingProject(remoteRobot, "sample-plsql")
        openFile(remoteRobot, "file.pkb")
        verifyNoIssuesFoundWhenNotConnected(this)

        enableConnectedModeFromCurrentFilePanel(this, PLSQL_PROJECT_KEY, true)
        verifyIssueTreeContainsMessages(this, "Remove this commented out code.")
    }

    private fun verifyIssueTreeContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    tabTitleContains("Current File") { select() }
                    expectedMessages.forEach { Assertions.assertThat(hasText(it)).isTrue() }
                }
            }
        }
    }

    private fun verifyNoIssuesFoundWhenNotConnected(remoteRobot: RemoteRobot) {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    tabTitleContains("Current File") { select() }
                    content("CurrentFilePanel") {
                        hasText("No issues found in the current opened file")
                    }
                }
            }
        }
    }

    companion object {

        private lateinit var token: String

        private val ORCHESTRATOR: OrchestratorExtension = OrchestratorUtils.defaultBuilderEnv()
            .setEdition(Edition.DEVELOPER)
            .activateLicense()
            .keepBundledPlugins()
            .restoreProfileAtStartup(FileLocation.ofClasspath("/plsql-issue.xml"))
            .build()

        @BeforeAll
        @JvmStatic
        fun prepare() {
            ORCHESTRATOR.start()

            val adminWsClient = OrchestratorUtils.newAdminWsClientWithUser(ORCHESTRATOR.server)

            ORCHESTRATOR.server.provisionProject(PLSQL_PROJECT_KEY, "Sample PLSQL Issues")
            ORCHESTRATOR.server.associateProjectToQualityProfile(
                PLSQL_PROJECT_KEY,
                "plsql",
                "SonarLint IT PLSQL Issue"
            )

            // Build and analyze project to raise issue
            OrchestratorUtils.executeBuildWithSonarScanner("projects/sample-plsql/", ORCHESTRATOR, PLSQL_PROJECT_KEY);

            token = OrchestratorUtils.generateToken(adminWsClient, "PLSQLTest")
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            ORCHESTRATOR.stop()
        }
    }

}
