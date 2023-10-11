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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.closeAllGotItTooltips
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.notification
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.defaultBuilderEnv
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.executeBuildWithMaven
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.generateToken
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.newAdminWsClientWithUser
import org.sonarlint.intellij.its.utils.ProjectBindingUtils.Companion.bindProjectToSonarQube


const val ISSUE_PROJECT_KEY = "sample-java-issues"

@DisabledIf("isCLionOrGoLand", disabledReason = "No Java Issues in CLion or GoLand")
class CurrentFileTabTest : BaseUiTest() {

    @Test
    fun should_display_issues_and_review_it_successfully() = uiTest {
        openExistingProject("sample-java-issues", true)
        bindProjectFromPanel()

        openFile("src/main/java/foo/Foo.java", "Foo.java")
        verifyIssueTreeContainsMessages(this, "Move this trailing comment on the previous empty line.")

        openReviewDialogFromList(this, "Move this trailing comment on the previous empty line.")
        changeStatusAndPressChange(this, "False Positive")
        confirm(this)
        verifyStatusWasSuccessfullyChanged(this)
    }

    @Test
    fun should_not_analyze_when_power_save_mode_enabled() = uiTest {
        openExistingProject("sample-java-issues")

        clickPowerSaveMode()

        openFile("src/main/java/foo/Foo.java", "Foo.java")

        verifyCurrentFileTabContainsMessages(
            "No analysis done on the current opened file",
            "This file is not automatically analyzed because power save mode is enabled",
        )

        clickPowerSaveMode()
    }

    private fun bindProjectFromPanel() {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    tab("Current File") { select() }
                    content("CurrentFilePanel") {
                        toolBarButton("Configure SonarLint").click()
                    }
                }
                bindProjectToSonarQube(
                    ORCHESTRATOR.server.url,
                    token,
                    ISSUE_PROJECT_KEY
                )
            }
        }
    }

    private fun openReviewDialogFromList(remoteRobot: RemoteRobot, issueMessage: String) {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    closeAllGotItTooltips()
                    tabTitleContains("Current File") { select() }
                    findText(issueMessage).rightClick()
                }
                actionMenuItem("Mark Issue as...") {
                    click()
                }
            }
        }
    }

    private fun changeStatusAndPressChange(remoteRobot: RemoteRobot, status: String) {
        with(remoteRobot) {
            idea {
                dialog("Mark Issue as Resolved on SonarQube") {
                    content(status) {
                        click()
                    }

                    pressButton("Mark Issue as...")
                }
            }
        }
    }

    private fun confirm(remoteRobot: RemoteRobot) {
        with(remoteRobot) {
            idea {
                dialog("Confirm marking issue as resolved") {
                    pressButton("Confirm")
                }
            }
        }
    }

    private fun verifyStatusWasSuccessfullyChanged(remoteRobot: RemoteRobot) {
        with(remoteRobot) {
            idea {
                notification("The issue was successfully marked as resolved")
                toolWindow("SonarLint") {
                    content("CurrentFilePanel") {
                        hasText("No issues found in the current opened file")
                    }
                }
            }
        }
    }

    private fun verifyIssueTreeContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    tabTitleContains("Current File") { select() }
                    expectedMessages.forEach { assertThat(hasText(it)).isTrue() }
                }
            }
        }
    }

    companion object {

        lateinit var token: String

        private val ORCHESTRATOR: OrchestratorExtension = defaultBuilderEnv()
            .setEdition(Edition.DEVELOPER)
            .activateLicense()
            .keepBundledPlugins()
            .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-with-issue.xml"))
            .build()

        @BeforeAll
        @JvmStatic
        fun prepare() {
            ORCHESTRATOR.start()

            val adminWsClient = newAdminWsClientWithUser(ORCHESTRATOR.server)

            ORCHESTRATOR.server.provisionProject(ISSUE_PROJECT_KEY, "Sample Java Issues")
            ORCHESTRATOR.server.associateProjectToQualityProfile(
                ISSUE_PROJECT_KEY,
                "java",
                "SonarLint IT Java Issue"
            )

            // Build and analyze project to raise issue
            executeBuildWithMaven("projects/sample-java-issues/pom.xml", ORCHESTRATOR);

            token = generateToken(adminWsClient, "CurrentFileTabTest")
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            ORCHESTRATOR.stop()
        }
    }
}
