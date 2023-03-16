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
import com.sonar.orchestrator.locator.FileLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.anActionLink
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.defaultBuilderEnv
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.executeBuildWithMaven
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.generateToken
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.newAdminWsClientWithUser
import org.sonarlint.intellij.its.utils.ProjectBindingUtils.Companion.bindProjectToSonarQube


const val TAINT_VULNERABILITY_PROJECT_KEY = "sample-java-taint-vulnerability"

@DisabledIf("isCLionOrGoLand", disabledReason = "No taint vulnerabilities in CLion or GoLand")
class TaintVulnerabilitiesTest : BaseUiTest() {

    @Test
    fun should_request_the_user_to_bind_project_when_not_bound() = uiTest {
        openExistingProject("sample-java-taint-vulnerability", true)

        verifyTaintTabContainsMessages(this, "The project is not bound to SonarQube/SonarCloud")
    }

    @Test
    fun should_display_sink() = uiTest {
        openExistingProject("sample-java-taint-vulnerability", true)
        bindProjectFromPanel()

        openFile("src/main/java/foo/FileWithSink.java", "FileWithSink.java")

        verifyTaintTabContainsMessages(
            this,
            "Found 1 issue in 1 file",
            "FileWithSink.java",
            "Change this code to not construct SQL queries directly from user-controlled data."
        )
    }

    private fun bindProjectFromPanel() {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    tab("Taint Vulnerabilities") { select() }
                    content("TaintVulnerabilitiesPanel") {
                        anActionLink("Configure Binding").click()
                    }
                }

                bindProjectToSonarQube(
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
                        expectedMessages.forEach { assertThat(hasText(it)).isTrue() }
                    }
                }
            }
        }
    }

    companion object {

        lateinit var token: String

        private val ORCHESTRATOR: Orchestrator = defaultBuilderEnv()
            .setEdition(Edition.DEVELOPER)
            .activateLicense()
            .keepBundledPlugins()
            .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-with-taint-vulnerability.xml"))
            .build()

        @BeforeAll
        @JvmStatic
        fun prepare() {
            ORCHESTRATOR.start()

            val adminWsClient = newAdminWsClientWithUser(ORCHESTRATOR.server)

            ORCHESTRATOR.server.provisionProject(TAINT_VULNERABILITY_PROJECT_KEY, "Sample Java Taint Vulnerability")
            ORCHESTRATOR.server.associateProjectToQualityProfile(
                TAINT_VULNERABILITY_PROJECT_KEY,
                "java",
                "SonarLint IT Java Taint Vulnerability"
            )

            // Build and analyze project to raise hotspot
            executeBuildWithMaven("projects/sample-java-taint-vulnerability/pom.xml", ORCHESTRATOR);

            token = generateToken(adminWsClient)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            ORCHESTRATOR.stop()
        }
    }
}
