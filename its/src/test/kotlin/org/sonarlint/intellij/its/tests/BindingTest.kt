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

import com.intellij.remoterobot.fixtures.ActionButtonFixture.Companion.byTooltipText
import com.intellij.remoterobot.fixtures.JButtonFixture.Companion.byText
import com.intellij.remoterobot.utils.keyboard
import com.sonar.orchestrator.Orchestrator
import com.sonar.orchestrator.build.SonarScanner
import com.sonar.orchestrator.container.Server
import com.sonar.orchestrator.locator.FileLocation
import com.sonar.orchestrator.locator.MavenLocation
import org.junit.Assume
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.clickWhenEnabled
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.isCLion
import org.sonarlint.intellij.its.fixtures.jRadioButtons
import org.sonarlint.intellij.its.fixtures.jTextField
import org.sonarlint.intellij.its.fixtures.jbTable
import org.sonarlint.intellij.its.fixtures.jbTextField
import org.sonarlint.intellij.its.fixtures.jbTextFields
import org.sonarlint.intellij.its.utils.ItUtils.SONAR_VERSION
import org.sonarqube.ws.client.HttpConnector
import org.sonarqube.ws.client.WsClient
import org.sonarqube.ws.client.WsClientFactories
import org.sonarqube.ws.client.issues.DoTransitionRequest
import org.sonarqube.ws.client.issues.SearchRequest
import org.sonarqube.ws.client.users.CreateRequest
import org.sonarqube.ws.client.usertokens.GenerateRequest
import java.io.File

class BindingTest : BaseUiTest() {

    @BeforeEach
    fun requirements() {
        Assume.assumeFalse(remoteRobot.isCLion())
    }

    @Test
    fun should_use_configured_project_and_module_bindings_for_analysis() = uiTest {
        // scala should only be supported in connected mode
        openExistingProject("sample-scala", true)
        bindProjectAndModuleInFileSettings()

        openFile("HelloProject.scala")

        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "HelloProject.scala",
        )
        clickCurrentFileIssue("Remove or correct this useless self-assignment.")
        verifyRuleDescriptionTabContains("Variables should not be self-assigned")

        openFile("mod/src/HelloModule.scala", "HelloModule.scala")

        verifyCurrentFileTabContainsMessages(
            "Found 1 issue in 1 file",
            "HelloModule.scala",
        )
        clickCurrentFileIssue("Add a nested comment explaining why this function is empty or complete the implementation.")
        verifyRuleDescriptionTabContains("Methods should not be empty")
    }

    private fun bindProjectAndModuleInFileSettings() {
        sonarLintGlobalSettings {
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
            tree {
                clickPath("Tools", "SonarLint", "Project Settings")
            }
            checkBox("Bind project to SonarQube / SonarCloud").select()
            pressOk()
            errorMessage("Connection should not be empty")

            comboBox("Connection:").selectItem("Orchestrator")
            pressOk()
            errorMessage("Project key should not be empty")

            jbTextField().text = PROJECT_KEY

            actionButton(byTooltipText("Add")).clickWhenEnabled()
            dialog("Select module") {
                jbTable().selectItemContaining("sample-scala-module")
                pressOk()
            }

            pressOk()
            errorMessage("Project key for module 'sample-scala-module' should not be empty")
            buttons(byText("Search in list..."))[1].click()
            dialog("Search Project in SonarQube") {
                jList {
                    clickItem(MODULE_PROJECT_KEY, false)
                }
                pressOk()
            }
            pressOk()
        }
    }

    companion object {

        lateinit var token: String

        private val ORCHESTRATOR: Orchestrator = Orchestrator.builderEnv()
            .defaultForceAuthentication()
            .setSonarVersion(SONAR_VERSION)
            .addPlugin(MavenLocation.of("org.sonarsource.slang", "sonar-scala-plugin", "1.8.3.2219"))
            .restoreProfileAtStartup(FileLocation.ofClasspath("/scala-sonarlint-self-assignment.xml"))
            .restoreProfileAtStartup(FileLocation.ofClasspath("/scala-sonarlint-empty-method.xml"))
            .build()

        private const val SONARLINT_USER = "sonarlint"
        private const val SONARLINT_PWD = "sonarlintpwd"
        private const val PROJECT_KEY = "sample-scala"
        private const val MODULE_PROJECT_KEY = "sample-scala-mod"

        @BeforeAll
        @JvmStatic
        fun prepare() {
            ORCHESTRATOR.start()

            val adminWsClient = newAdminWsClient()
            adminWsClient.users()
                .create(CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"))

            ORCHESTRATOR.server.provisionProject(PROJECT_KEY, "Sample Scala")
            ORCHESTRATOR.server.associateProjectToQualityProfile(PROJECT_KEY, "scala", "SonarLint IT Scala")
            ORCHESTRATOR.server.provisionProject(MODULE_PROJECT_KEY, "Sample Scala Module ")
            ORCHESTRATOR.server.associateProjectToQualityProfile(MODULE_PROJECT_KEY, "scala", "SonarLint IT Scala Module")

            ORCHESTRATOR.executeBuild(
                SonarScanner.create(File("projects/sample-scala/"))
                    .setProperty("sonar.login", SONARLINT_USER)
                    .setProperty("sonar.password", SONARLINT_PWD)
                    .setProperty("sonar.projectKey", PROJECT_KEY)
            )
            ORCHESTRATOR.executeBuild(
                SonarScanner.create(File("projects/sample-scala/mod/"))
                    .setProperty("sonar.login", SONARLINT_USER)
                    .setProperty("sonar.password", SONARLINT_PWD)
                    .setProperty("sonar.projectKey", MODULE_PROJECT_KEY)
            )

            val generateRequest = GenerateRequest()
            generateRequest.name = "TestUser"
            token = adminWsClient.userTokens().generate(generateRequest).token


            val searchRequest = SearchRequest()
            searchRequest.s = "FILE_LINE"
            searchRequest.projects = listOf(MODULE_PROJECT_KEY)
            val response = adminWsClient.issues().search(searchRequest)
            val firstIssueKey = response.issuesList[0].key
            adminWsClient.issues().doTransition(DoTransitionRequest().setIssue(firstIssueKey).setTransition("wontfix"))
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
