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
import com.sonar.orchestrator.Orchestrator
import com.sonar.orchestrator.container.Edition
import com.sonar.orchestrator.http.HttpMethod
import com.sonar.orchestrator.locator.FileLocation
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.utils.OrchestratorUtils
import org.sonarlint.intellij.its.utils.ProjectBindingUtils

@DisabledIf("isCLionOrGoLand", disabledReason = "No Java Issues in CLion or GoLand")
class FocusOnNewCodeTest : BaseUiTest() {

    @Test
    fun should_display_new_focus_mode() = uiTest {
        openExistingProject("sample-java-taint-vulnerability", true)
        bindProjectFromPanel()

        openFile("src/main/java/foo/FileWithSink.java", "FileWithSink.java")

        verifyReportTabContainsMessages(
            this,
            "Found 2 new issues in 1 file from last 1 days",
            "No older issues",
            "Found 1 new Security Hotspot in 1 file from last 1 days",
            "No older Security Hotspots"
        )

        verifyTaintTabContainsMessages(
            this,
            "Found 1 new issue in 1 file from last 1 days",
            "FileWithSink.java",
            "Change this code to not construct SQL queries directly from user-controlled data.",
            "No older issues"
        )

        verifyHotspotTabContainsMessages(
            this,
            "Found 1 new Security Hotspot in 1 file from last 1 days",
            "No older Security Hotspots"
        )

        verifyIssueTreeContainsMessages(
            this,
            "Found 2 new issues in 1 file from last 1 days",
            "No older issues"
        )
    }

    private fun bindProjectFromPanel() {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    tab("Taint Vulnerabilities") { select() }
                    content("TaintVulnerabilitiesPanel") {
                        findText("Configure Binding").click()
                    }
                }

                ProjectBindingUtils.bindProjectToSonarQube(
                    ORCHESTRATOR.server.url,
                    token,
                    TAINT_VULNERABILITY_PROJECT_KEY
                )
            }
        }
    }

    private fun verifyTaintTabContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    tabTitleContains("Taint Vulnerabilities") { select() }
                    content("TaintVulnerabilitiesPanel") {
                        expectedMessages.forEach {
                            Assertions.assertThat(hasText(it)).isTrue()
                        }
                    }
                }
            }
        }
    }

    private fun verifyHotspotTabContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    tabTitleContains("Security Hotspots") { select() }
                    content("SecurityHotspotsPanel") {
                        expectedMessages.forEach { Assertions.assertThat(hasText(it)).isTrue() }
                    }
                }
            }
        }
    }

    private fun verifyReportTabContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
        with(remoteRobot) {
            idea {
                analyzeFile()
                toolWindow("SonarLint") {
                    ensureOpen()
                    tabTitleContains("Report") { select() }
                    content("ReportPanel") {
                        toolBarButton("Set Focus on New Code").click()
                        expectedMessages.forEach { Assertions.assertThat(hasText(it)).isTrue() }
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
                    content("CurrentFilePanel") {
                        expectedMessages.forEach { Assertions.assertThat(hasText(it)).isTrue() }
                        toolBarButton("Set Focus on New Code").click()
                    }
                }
            }
        }
    }

    companion object {

        lateinit var token: String

        private val ORCHESTRATOR: Orchestrator = OrchestratorUtils.defaultBuilderEnv()
            .setEdition(Edition.DEVELOPER)
            .activateLicense()
            .keepBundledPlugins()
            .restoreProfileAtStartup(FileLocation.ofClasspath("/java-taint-hotspot-issue.xml"))
            .build()

        @BeforeAll
        @JvmStatic
        fun prepare() {
            ORCHESTRATOR.start()

            val adminWsClient = OrchestratorUtils.newAdminWsClientWithUser(ORCHESTRATOR.server)

            ORCHESTRATOR.server.provisionProject(TAINT_VULNERABILITY_PROJECT_KEY, "Sample Java Taint Vulnerability")
            ORCHESTRATOR.server.associateProjectToQualityProfile(
                TAINT_VULNERABILITY_PROJECT_KEY,
                "java",
                "SonarLint IT Java Taint Hotspot Issue"
            )
            ORCHESTRATOR.server.newHttpCall("/api/new_code_periods/set")
                .setMethod(HttpMethod.POST)
                .setAdminCredentials()
                .setParam("type", "NUMBER_OF_DAYS")
                .setParam("value", 1.toString())
                .execute()

            // Build and analyze project to raise hotspot
            OrchestratorUtils.executeBuildWithMaven("projects/sample-java-taint-vulnerability/pom.xml", ORCHESTRATOR)

            // Analyze a second time for the measure to be returned by the web API
            OrchestratorUtils.executeBuildWithMaven("projects/sample-java-taint-vulnerability/pom.xml", ORCHESTRATOR)

            token = OrchestratorUtils.generateToken(adminWsClient)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            ORCHESTRATOR.stop()
        }
    }
}
