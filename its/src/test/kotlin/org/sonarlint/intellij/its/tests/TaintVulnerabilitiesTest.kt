/*
 * SonarLint for IntelliJ IDEA ITs
 * Copyright (C) 2015-2021 SonarSource
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
import com.intellij.remoterobot.fixtures.ActionButtonFixture.Companion.byTooltipText
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import com.sonar.orchestrator.Orchestrator
import com.sonar.orchestrator.build.MavenBuild
import com.sonar.orchestrator.container.Edition
import com.sonar.orchestrator.container.Server
import com.sonar.orchestrator.locator.FileLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.clickWhenEnabled
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.isCLion
import org.sonarlint.intellij.its.fixtures.jRadioButtons
import org.sonarlint.intellij.its.fixtures.jTextField
import org.sonarlint.intellij.its.fixtures.jbTextField
import org.sonarlint.intellij.its.fixtures.jbTextFields
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.utils.ItUtils
import org.sonarqube.ws.client.HttpConnector
import org.sonarqube.ws.client.WsClient
import org.sonarqube.ws.client.WsClientFactories
import org.sonarqube.ws.client.users.CreateRequest
import org.sonarqube.ws.client.usertokens.GenerateRequest
import java.io.File
import java.time.Duration


const val TAINT_VULNERABILITY_PROJECT_KEY = "sample-java-taint-vulnerability"

class TaintVulnerabilitiesTest : BaseUiTest() {

    @Test
    fun should_request_the_user_to_bind_project_when_not_bound() = uiTest {
        Assume.assumeFalse(this.isCLion())

        openExistingProject("sample-java-taint-vulnerability", true)

        verifyTaintTabContainsMessages(this, "The project is not bound to SonarQube/SonarCloud")
    }

    @Test
    fun should_display_sink() = uiTest {
        Assume.assumeFalse(this.isCLion())

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
                    tab("Taint vulnerabilities") { select() }
                    content("TaintVulnerabilitiesPanel") {
                        jLabel("Configure Binding").click()
                    }
                }
                dialog("Project Settings") {
                    checkBox("Bind project to SonarQube / SonarCloud").select()
                    button("Configure the connection...").click()
                    dialog("SonarLint") {
                        actionButton(byTooltipText("Add")).clickWhenEnabled()
                        dialog("New Connection: Server Details") {
                            keyboard { enterText("Orchestrator") }
                            jRadioButtons()[1].select()
                            jbTextFields()[1].text = ORCHESTRATOR.server.url
                            button("Next").click()
                        }
                        dialog("New Connection: Authentication") {
                            jTextField().text = token
                            button("Next").click()
                        }
                        dialog("New Connection: Configure Notifications") {
                            button("Next").click()
                        }
                        dialog("New Connection: Configuration completed") {
                            button("Finish").click()
                        }
                        button("OK").click()
                    }
                    comboBox("Connection:").selectItem("Orchestrator")
                    jbTextField().text = TAINT_VULNERABILITY_PROJECT_KEY
                    button("OK").click()
                    // wait for binding fully established
                    waitFor(Duration.ofSeconds(20)) { !isShowing }
                }
            }
        }
    }

    private fun verifyTaintTabContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    tabTitleContains("Taint vulnerabilities") { select() }
                    content("TaintVulnerabilitiesPanel") {
                        expectedMessages.forEach { assertThat(hasText(it)).isTrue() }
                    }
                }
            }
        }
    }

    companion object {

        lateinit var token: String

        private val ORCHESTRATOR: Orchestrator = Orchestrator.builderEnv()
            .defaultForceAuthentication()
            .setSonarVersion(ItUtils.SONAR_VERSION)
            .setEdition(Edition.DEVELOPER)
            .activateLicense()
            .keepBundledPlugins()
            .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-with-taint-vulnerability.xml"))
            .build()

        private const val SONARLINT_USER = "sonarlint"
        private const val SONARLINT_PWD = "sonarlintpwd"

        @BeforeAll
        @JvmStatic
        fun prepare() {
            ORCHESTRATOR.start()

            val adminWsClient = newAdminWsClient()
            adminWsClient.users()
                .create(CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"))

            ORCHESTRATOR.server.provisionProject(TAINT_VULNERABILITY_PROJECT_KEY, "Sample Java Taint Vulnerability")
            ORCHESTRATOR.server.associateProjectToQualityProfile(
                TAINT_VULNERABILITY_PROJECT_KEY,
                "java",
                "SonarLint IT Java Taint Vulnerability"
            )

            // Build and analyze project to raise hotspot
            val file = File("projects/sample-java-taint-vulnerability/pom.xml")
            ORCHESTRATOR.executeBuild(
                MavenBuild.create(file)
                    .setCleanPackageSonarGoals()
                    .setProperty("sonar.login", SONARLINT_USER)
                    .setProperty("sonar.password", SONARLINT_PWD)
            )

            val generateRequest = GenerateRequest()
            generateRequest.name = "TestUser"
            token = adminWsClient.userTokens().generate(generateRequest).token
        }

        private fun newAdminWsClient(): WsClient {
            val server = ORCHESTRATOR.server
            return WsClientFactories.getDefault().newClient(
                HttpConnector.newBuilder()
                    .url(server.url)
                    .credentials(Server.ADMIN_LOGIN, Server.ADMIN_PASSWORD)
                    .build()
            )
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            ORCHESTRATOR.stop()
        }
    }
}
