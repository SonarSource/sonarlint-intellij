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

import com.google.protobuf.InvalidProtocolBufferException
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ActionButtonFixture.Companion.byTooltipText
import com.intellij.remoterobot.fixtures.ActionLinkFixture.Companion.byText
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import com.sonar.orchestrator.container.Edition
import com.sonar.orchestrator.junit5.OrchestratorExtension
import com.sonar.orchestrator.locator.FileLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.DisabledIf
import org.junit.jupiter.api.extension.RegisterExtension
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.clickWhenEnabled
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.editor
import org.sonarlint.intellij.its.fixtures.fileBrowserDialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.jPasswordField
import org.sonarlint.intellij.its.fixtures.jRadioButtons
import org.sonarlint.intellij.its.fixtures.jbTable
import org.sonarlint.intellij.its.fixtures.jbTextField
import org.sonarlint.intellij.its.fixtures.jbTextFields
import org.sonarlint.intellij.its.fixtures.notification
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.defaultBuilderEnv
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.executeBuildWithMaven
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.executeBuildWithSonarScanner
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.generateToken
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.newAdminWsClientWithUser
import org.sonarlint.intellij.its.utils.ProjectBindingUtils.Companion.bindProjectToSonarQube
import org.sonarlint.intellij.its.utils.optionalStep
import org.sonarqube.ws.client.WsClient
import org.sonarqube.ws.client.issues.DoTransitionRequest
import org.sonarqube.ws.client.issues.SearchRequest
import org.sonarqube.ws.client.settings.SetRequest
import java.net.URL
import java.time.Duration.ofSeconds

@DisabledIf("isCLionOrGoLand")
class AllTest : BaseUiTest() {

    companion object {
        @JvmField
        @RegisterExtension
        val ORCHESTRATOR: OrchestratorExtension = defaultBuilderEnv()
            .setEdition(Edition.DEVELOPER)
            .activateLicense()
            .keepBundledPlugins()
            .build()

        private lateinit var adminWsClient: WsClient

        const val TAINT_VULNERABILITY_PROJECT_KEY = "sample-java-taint-vulnerability"
        const val SECURITY_HOTSPOT_PROJECT_KEY = "sample-java-hotspot"
        const val PROJECT_KEY = "sample-scala"
        const val MODULE_PROJECT_KEY = "sample-scala-mod"

        private var firstHotspotKey: String? = null
        lateinit var token: String

        @Throws(InvalidProtocolBufferException::class)
        private fun getFirstHotspotKey(client: WsClient): String? {
            val searchRequest = org.sonarqube.ws.client.hotspots.SearchRequest()
            searchRequest.projectKey = SECURITY_HOTSPOT_PROJECT_KEY
            val searchResults = client.hotspots().search(searchRequest)
            val hotspot = searchResults.hotspotsList[0]
            return hotspot.key
        }

        @JvmStatic
        @BeforeAll
        fun createSonarLintUser() {
            adminWsClient = newAdminWsClientWithUser(ORCHESTRATOR.server)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisabledIf("isCLionOrGoLand")
    inner class BindingTest : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/scala-sonarlint-self-assignment.xml"))
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/scala-sonarlint-empty-method.xml"))

            ORCHESTRATOR.server.provisionProject(PROJECT_KEY, "Sample Scala")
            ORCHESTRATOR.server.associateProjectToQualityProfile(PROJECT_KEY, "scala", "SonarLint IT Scala")
            ORCHESTRATOR.server.provisionProject(MODULE_PROJECT_KEY, "Sample Scala Module ")
            ORCHESTRATOR.server.associateProjectToQualityProfile(MODULE_PROJECT_KEY, "scala", "SonarLint IT Scala Module")

            val excludeFileRequest = SetRequest()
            excludeFileRequest.key = "sonar.exclusions"
            excludeFileRequest.component = MODULE_PROJECT_KEY
            excludeFileRequest.values = listOf("src/Excluded.scala")
            adminWsClient.settings().set(excludeFileRequest)

            executeBuildWithSonarScanner("projects/sample-scala/", ORCHESTRATOR, PROJECT_KEY)
            executeBuildWithSonarScanner("projects/sample-scala/mod/", ORCHESTRATOR, MODULE_PROJECT_KEY)

            token = generateToken(adminWsClient, "BindingTest")

            val searchRequest = SearchRequest()
            searchRequest.s = "FILE_LINE"
            searchRequest.projects = listOf(MODULE_PROJECT_KEY)
            val response = adminWsClient.issues().search(searchRequest)
            val firstIssueKey = response.issuesList[0].key
            adminWsClient.issues().doTransition(DoTransitionRequest().setIssue(firstIssueKey).setTransition("wontfix"))
        }

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
                waitFor(ofSeconds(2)) { buttons(byText("Search in list...")).isNotEmpty() }
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

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisabledIf("isCLionOrGoLand", disabledReason = "No Java security hotspots in CLion or GoLand")
    inner class OpenInIdeTest : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-hotspot.xml"))

            ORCHESTRATOR.server.provisionProject(SECURITY_HOTSPOT_PROJECT_KEY, "Sample Java")
            ORCHESTRATOR.server.associateProjectToQualityProfile(SECURITY_HOTSPOT_PROJECT_KEY, "java", "SonarLint IT Java Hotspot")

            // Build and analyze project to raise hotspot
            executeBuildWithMaven("projects/sample-java-hotspot/pom.xml", ORCHESTRATOR)

            firstHotspotKey = getFirstHotspotKey(adminWsClient)

            token = generateToken(adminWsClient, "OpenInIdeTest")
        }

        @Test
        fun opensHotspotAfterConfiguringConnectionAndBinding() = uiTest {
            openExistingProject("sample-java-hotspot", true)

            triggerOpenHotspotRequest()

            createConnection(this)
            bindRecentProject(this)
            verifyHotspotOpened(this)
        }

        private fun triggerOpenHotspotRequest() {
            URL("http://localhost:64120/sonarlint/api/hotspots/show?project=$PROJECT_KEY&hotspot=$firstHotspotKey&server=${ORCHESTRATOR.server.url}")
                .readText()
        }

        private fun createConnection(robot: RemoteRobot) {
            with(robot) {
                idea {
                    dialog("Opening Security Hotspot...") {
                        button("Create connection").click()
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
                        pressFinishOrCreate()
                    }
                }
            }
        }

        private fun bindRecentProject(robot: RemoteRobot) {
            with(robot) {
                idea {
                    dialog("Opening Security Hotspot...") {
                        button("Select project").click()
                    }
                    dialog("Select a project") {
                        button("Open or import").click()
                    }
                    fileBrowserDialog(arrayOf("Select Path")) {
                        selectProjectFile("sample-java-hotspot", true)
                    }
                    optionalStep {
                        dialog("Open Project") {
                            button("This Window").click()
                        }
                    }
                    dialog("Opening Security Hotspot...") {
                        button("Yes").click()
                    }
                }
            }
        }

        private fun verifyHotspotOpened(robot: RemoteRobot) {
            verifyEditorOpened(robot)
            verifyToolWindowFilled(robot)
        }

        private fun verifyEditorOpened(robot: RemoteRobot) {
            with(robot) {
                idea {
                    editor("Foo.java")
                }
            }
        }

        private fun verifyToolWindowFilled(robot: RemoteRobot) {
            with(robot) {
                idea {
                    toolWindow("SonarLint") {
                        tabTitleContains("Security Hotspots") {
                            content("SecurityHotspotsPanel") {
                                assertThat(hasText("Make sure using this hardcoded IP address is safe here.")).isTrue()
                            }
                        }
                    }
                }
            }
        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisabledIf("isCLionOrGoLand", disabledReason = "No Java security hotspots in CLion or GoLand")
    inner class SecurityHotspotTabTest : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-hotspot.xml"))

            ORCHESTRATOR.server.provisionProject(SECURITY_HOTSPOT_PROJECT_KEY, "Sample Java Hotspot")
            ORCHESTRATOR.server.associateProjectToQualityProfile(SECURITY_HOTSPOT_PROJECT_KEY, "java", "SonarLint IT Java Hotspot")

            // Build and analyze project to raise hotspot
            executeBuildWithMaven("projects/sample-java-hotspot/pom.xml", ORCHESTRATOR)

            token = generateToken(adminWsClient, "SecurityHotspotTabTest")
        }

        @Test
        fun should_request_the_user_to_bind_project_when_not_bound() = uiTest {
            openExistingProject("sample-java-hotspot", true)
            verifySecurityHotspotTabContainsMessages(this, "The project is not bound, please bind it to SonarQube 9.7+ or SonarCloud")
        }

        @Test
        fun should_display_security_hotspots_and_review_it_successfully() = uiTest {
            openExistingProject("sample-java-hotspot", true)
            bindProjectFromPanel()

            openFile("src/main/java/foo/Foo.java", "Foo.java")
            verifySecurityHotspotTreeContainsMessages(this, "Make sure using this hardcoded IP address is safe here.")

            openReviewDialogFromList(this, "Make sure using this hardcoded IP address is safe here.")
            changeStatusAndPressChange(this, "Acknowledged")
            verifyStatusWasSuccessfullyChanged(this, "SonarLint - Security Hotspot review")
        }

        private fun bindProjectFromPanel() {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tab("Security Hotspots") { select() }
                        content("SecurityHotspotsPanel") {
                            findText("Configure Binding").click()
                        }
                    }
                    bindProjectToSonarQube(
                        ORCHESTRATOR.server.url,
                        token,
                        SECURITY_HOTSPOT_PROJECT_KEY
                    )
                }
            }
        }

        private fun openReviewDialogFromList(remoteRobot: RemoteRobot, securityHotspotMessage: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Security Hotspots") { select() }
                        content("SecurityHotspotTree") {
                            findText(securityHotspotMessage).rightClick()
                        }
                    }
                    actionMenuItem("Review Security Hotspot") {
                        click()
                    }
                }
            }
        }

        private fun changeStatusAndPressChange(remoteRobot: RemoteRobot, status: String) {
            with(remoteRobot) {
                idea {
                    dialog("Change Security Hotspot Status on SonarQube") {
                        content(status) {
                            click()
                        }
                        pressButton("Change Status")
                    }
                }
            }
        }

        private fun verifyStatusWasSuccessfullyChanged(remoteRobot: RemoteRobot, title: String) {
            with(remoteRobot) {
                idea {
                    notification(title) {
                        hasText("The Security Hotspot status was successfully updated!")
                    }
                    toolWindow("SonarLint") {
                        content("SecurityHotspotsPanel") {
                            hasText("No Security Hotspot found.")
                        }
                    }
                }
            }
        }

        private fun verifySecurityHotspotTabContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Security Hotspots") { select() }
                        content("SecurityHotspotsPanel") {
                            expectedMessages.forEach { assertThat(hasText(it)).isTrue() }
                        }
                    }
                }
            }
        }

        private fun verifySecurityHotspotTreeContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Security Hotspots") { select() }
                        content("SecurityHotspotTree") {
                            expectedMessages.forEach { assertThat(hasText(it)).isTrue() }
                        }
                    }
                }
            }
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisabledIf("isCLionOrGoLand", disabledReason = "No taint vulnerabilities in CLion or GoLand")
    inner class TaintVulnerabilitiesTest : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-taint-vulnerability.xml"))

            ORCHESTRATOR.server.provisionProject(TAINT_VULNERABILITY_PROJECT_KEY, "Sample Java Taint Vulnerability")
            ORCHESTRATOR.server.associateProjectToQualityProfile(
                TAINT_VULNERABILITY_PROJECT_KEY,
                "java",
                "SonarLint IT Java Taint Vulnerability"
            )

            // Build and analyze project to raise hotspot
            executeBuildWithMaven("projects/sample-java-taint-vulnerability/pom.xml", ORCHESTRATOR)

            token = generateToken(adminWsClient, "TaintVulnerabilitiesTest")
        }

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
                            findText("Configure Binding").click()
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
    }

}
