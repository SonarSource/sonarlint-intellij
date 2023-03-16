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

import com.intellij.remoterobot.fixtures.ActionButtonFixture.Companion.byTooltipText
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.JButtonFixture.Companion.byText
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import com.sonar.orchestrator.Orchestrator
import com.sonar.orchestrator.locator.FileLocation
import com.sonar.orchestrator.locator.MavenLocation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.clickWhenEnabled
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.jPasswordField
import org.sonarlint.intellij.its.fixtures.jRadioButtons
import org.sonarlint.intellij.its.fixtures.jbTable
import org.sonarlint.intellij.its.fixtures.jbTextField
import org.sonarlint.intellij.its.fixtures.jbTextFields
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.defaultBuilderEnv
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.executeBuildWithSonarScanner
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.generateToken
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.newAdminWsClientWithUser
import org.sonarqube.ws.client.issues.DoTransitionRequest
import org.sonarqube.ws.client.issues.SearchRequest
import org.sonarqube.ws.client.settings.SetRequest
import java.time.Duration.ofSeconds

@DisabledIf("isCLionOrGoLand")
class BindingTest : BaseUiTest() {

    @Test
    fun should_use_configured_project_and_module_bindings_for_analysis() = uiTest {
        // scala should only be supported in connected mode
        openExistingProject("sample-scala", true)
        verifyCurrentFileShowsCard("EmptyCard")

        openFile("HelloProject.scala")
        verifyCurrentFileShowsCard("NotConnectedCard")

        bindProjectAndModuleInFileSettings()
        // Wait for re-analysis to happen
        with(remoteRobot) {
            idea {
                waitBackgroundTasksFinished()
            }
        }
        verifyCurrentFileShowsCard("ConnectedCard")
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

        openFile("mod/src/Excluded.scala", "Excluded.scala")
        verifyCurrentFileTabContainsMessages(
            "No analysis done on the current opened file",
            "This file is not automatically analyzed",
        )
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
                jPasswordField().text = token
                button("Next").click()
            }
            dialog("New Connection: Configure Notifications") {
                button("Next").click()
            }
            dialog("New Connection: Configuration completed") {
                pressFinishOrCreate()
            }
            tree {
                clickPath("Tools", "SonarLint", "Project Settings")
            }
            checkBox("Bind project to SonarQube / SonarCloud").select()
            pressOk()
            errorMessage("Connection should not be empty")

            comboBox("Connection:").click()
            remoteRobot.find<ContainerFixture>(byXpath("//div[@class='CustomComboPopup']")).apply {
                waitFor(ofSeconds(5)) { hasText("Orchestrator") }
                findText("Orchestrator").click()
            }
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
            dialog("Select SonarQube Project To Bind") {
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

        private val ORCHESTRATOR: Orchestrator = defaultBuilderEnv()
            .addPlugin(MavenLocation.of("org.sonarsource.slang", "sonar-scala-plugin", "1.8.3.2219"))
            .restoreProfileAtStartup(FileLocation.ofClasspath("/scala-sonarlint-self-assignment.xml"))
            .restoreProfileAtStartup(FileLocation.ofClasspath("/scala-sonarlint-empty-method.xml"))
            .build()

        private const val PROJECT_KEY = "sample-scala"
        private const val MODULE_PROJECT_KEY = "sample-scala-mod"

        @BeforeAll
        @JvmStatic
        fun prepare() {
            ORCHESTRATOR.start()

            val adminWsClient = newAdminWsClientWithUser(ORCHESTRATOR.server)

            ORCHESTRATOR.server.provisionProject(PROJECT_KEY, "Sample Scala")
            ORCHESTRATOR.server.associateProjectToQualityProfile(PROJECT_KEY, "scala", "SonarLint IT Scala")
            ORCHESTRATOR.server.provisionProject(MODULE_PROJECT_KEY, "Sample Scala Module ")
            ORCHESTRATOR.server.associateProjectToQualityProfile(MODULE_PROJECT_KEY, "scala", "SonarLint IT Scala Module")

            val excludeFileRequest = SetRequest()
            excludeFileRequest.key = "sonar.exclusions"
            excludeFileRequest.component = MODULE_PROJECT_KEY
            excludeFileRequest.values = listOf("src/Excluded.scala")
            adminWsClient.settings().set(excludeFileRequest)

            executeBuildWithSonarScanner("projects/sample-scala/", ORCHESTRATOR, PROJECT_KEY);
            executeBuildWithSonarScanner("projects/sample-scala/mod/", ORCHESTRATOR, MODULE_PROJECT_KEY);

            token = generateToken(adminWsClient)

            val searchRequest = SearchRequest()
            searchRequest.s = "FILE_LINE"
            searchRequest.projects = listOf(MODULE_PROJECT_KEY)
            val response = adminWsClient.issues().search(searchRequest)
            val firstIssueKey = response.issuesList[0].key
            adminWsClient.issues().doTransition(DoTransitionRequest().setIssue(firstIssueKey).setTransition("wontfix"))
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            ORCHESTRATOR.stop()
        }
    }

}
