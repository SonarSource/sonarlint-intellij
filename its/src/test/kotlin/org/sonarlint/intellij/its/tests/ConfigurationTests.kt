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
package org.sonarlint.intellij.its.tests

import com.sonar.orchestrator.container.Edition
import com.sonar.orchestrator.junit5.OrchestratorExtension
import com.sonar.orchestrator.locator.FileLocation
import kotlin.random.Random
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.extension.RegisterExtension
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.tests.domain.ConnectionFromToolWindowTests.bindProjectFromToolWindow
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.changeStatusOnSonarCloudAndPressChange
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.clickCurrentFileIssue
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.confirm
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.enableConnectedModeFromCurrentFilePanel
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.openIssueReviewDialogFromList
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileRuleDescriptionTabContains
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileShowsCard
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyIssueStatusWasSuccessfullyChanged
import org.sonarlint.intellij.its.tests.domain.SharedConfigurationTests.Companion.importConfiguration
import org.sonarlint.intellij.its.tests.domain.SharedConfigurationTests.Companion.shareConfiguration
import org.sonarlint.intellij.its.utils.OpeningUtils.closeProject
import org.sonarlint.intellij.its.utils.OpeningUtils.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.openFile
import org.sonarlint.intellij.its.utils.OrchestratorUtils.defaultBuilderEnv
import org.sonarlint.intellij.its.utils.OrchestratorUtils.executeBuildWithMaven
import org.sonarlint.intellij.its.utils.OrchestratorUtils.executeBuildWithSonarScanner
import org.sonarlint.intellij.its.utils.OrchestratorUtils.generateTokenNameAndValue
import org.sonarlint.intellij.its.utils.OrchestratorUtils.newAdminWsClientWithUser
import org.sonarlint.intellij.its.utils.ProjectBindingUtils.bindProjectAndModuleInFileSettings
import org.sonarlint.intellij.its.utils.SettingsUtils.addSonarCloudConnection
import org.sonarlint.intellij.its.utils.SettingsUtils.clearConnections
import org.sonarlint.intellij.its.utils.SettingsUtils.clearConnectionsAndAddSonarQubeConnection
import org.sonarlint.intellij.its.utils.SonarCloudUtils.SONARCLOUD_STAGING_URL
import org.sonarlint.intellij.its.utils.SonarCloudUtils.analyzeSonarCloudWithMaven
import org.sonarlint.intellij.its.utils.SonarCloudUtils.associateSonarCloudProjectToQualityProfile
import org.sonarlint.intellij.its.utils.SonarCloudUtils.cleanupProjects
import org.sonarlint.intellij.its.utils.SonarCloudUtils.newAdminSonarCloudWsClientWithUser
import org.sonarlint.intellij.its.utils.SonarCloudUtils.provisionSonarCloudProfile
import org.sonarlint.intellij.its.utils.SonarCloudUtils.restoreSonarCloudProfile
import org.sonarqube.ws.client.WsClient
import org.sonarqube.ws.client.issues.DoTransitionRequest
import org.sonarqube.ws.client.issues.SearchRequest
import org.sonarqube.ws.client.settings.SetRequest
import org.sonarqube.ws.client.usertokens.GenerateRequest
import org.sonarqube.ws.client.usertokens.RevokeRequest

@Tag("ConfigurationTests")
@EnabledIf("isIdeaCommunity")
class ConfigurationTests : BaseUiTest() {

    companion object {
        @JvmField
        @RegisterExtension
        val ORCHESTRATOR: OrchestratorExtension = defaultBuilderEnv()
            .setEdition(Edition.DEVELOPER)
            .activateLicense()
            .addBundledPluginToKeep("sonar-java")
            .addBundledPluginToKeep("sonar-security")
            .addBundledPluginToKeep("sonar-scala")
            .addBundledPluginToKeep("sonar-php")
            .addBundledPluginToKeep("sonar-python")
            .addBundledPluginToKeep("sonar-kotlin")
            .addBundledPluginToKeep("sonar-go")
            .build()

        private lateinit var adminWsClient: WsClient
        private lateinit var adminSonarCloudWsClient: WsClient

        const val SCALA_PROJECT_KEY = "sample-scala"
        const val SCALA_MODULE_PROJECT_KEY = "sample-scala-mod"
        const val ISSUE_PROJECT_KEY = "sli-java-issues"
        const val SHARED_CONNECTED_MODE_KEY = "shared-connected-mode"
        const val SHARED_CONNECTED_MODE_MODULE_KEY = "shared-connected-mode-module"
        val SONARCLOUD_ISSUE_PROJECT_KEY = projectKey(ISSUE_PROJECT_KEY)

        private var firstIssueKey: String? = null
        private var firstSCIssueKey: String? = null
        lateinit var tokenName: String
        lateinit var tokenValue: String
        lateinit var sonarCloudToken: String
        private val sonarCloudTokenName = "SLCORE-IT-${System.currentTimeMillis()}"

        private fun projectKey(key: String): String {
            val randomPositiveInt = Random.nextInt(Int.MAX_VALUE)
            return "sonarlint-its-$key-$randomPositiveInt"
        }

        private fun getFirstIssueKey(client: WsClient, projectKey: String): String? {
            val searchRequest = SearchRequest()
            searchRequest.projects = listOf(projectKey)
            val searchResults = client.issues().search(searchRequest)
            val issue = searchResults.issuesList[0]
            return issue.key
        }

        private fun getFirstSCIssueKey(client: WsClient): String? {
            val searchRequest = SearchRequest()
            searchRequest.projects = listOf(SONARCLOUD_ISSUE_PROJECT_KEY)
            val searchResults = client.issues().search(searchRequest)
            val issue = searchResults.issuesList[0]
            return issue.key
        }

        @JvmStatic
        @BeforeAll
        fun createSonarLintUser() {
            adminWsClient = newAdminWsClientWithUser(ORCHESTRATOR.server)
            val token = generateTokenNameAndValue(adminWsClient, "sonarlintUser")
            tokenName = token.first
            tokenValue = token.second

            adminSonarCloudWsClient = newAdminSonarCloudWsClientWithUser(SONARCLOUD_STAGING_URL)
            sonarCloudToken = adminSonarCloudWsClient.userTokens()
                .generate(GenerateRequest().setName(sonarCloudTokenName))
                .token

            clearConnectionsAndAddSonarQubeConnection(ORCHESTRATOR.server.url, tokenValue)
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            cleanupProjects(adminSonarCloudWsClient, SONARCLOUD_ISSUE_PROJECT_KEY)
            adminSonarCloudWsClient.userTokens().revoke(RevokeRequest().setName(sonarCloudTokenName))
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SampleScalaTests : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/scala-sonarlint-self-assignment.xml"))
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/scala-sonarlint-empty-method.xml"))

            ORCHESTRATOR.server.provisionProject(SCALA_PROJECT_KEY, "Sample Scala")
            ORCHESTRATOR.server.associateProjectToQualityProfile(SCALA_PROJECT_KEY, "scala", "SonarLint IT Scala")
            ORCHESTRATOR.server.provisionProject(SCALA_MODULE_PROJECT_KEY, "Sample Scala Module")
            ORCHESTRATOR.server.associateProjectToQualityProfile(SCALA_MODULE_PROJECT_KEY, "scala", "SonarLint IT Scala Module")

            val excludeFileRequest = SetRequest()
            excludeFileRequest.key = "sonar.exclusions"
            excludeFileRequest.component = SCALA_MODULE_PROJECT_KEY
            excludeFileRequest.values = listOf("src/Excluded.scala")
            adminWsClient.settings().set(excludeFileRequest)

            executeBuildWithSonarScanner("projects/sample-scala/", ORCHESTRATOR, SCALA_PROJECT_KEY)
            executeBuildWithSonarScanner("projects/sample-scala/mod/", ORCHESTRATOR, SCALA_MODULE_PROJECT_KEY)

            val searchRequest = SearchRequest()
            searchRequest.s = "FILE_LINE"
            searchRequest.projects = listOf(SCALA_MODULE_PROJECT_KEY)
            val response = adminWsClient.issues().search(searchRequest)
            val firstIssueKey = response.issuesList[0].key
            adminWsClient.issues().doTransition(DoTransitionRequest().setIssue(firstIssueKey).setTransition("wontfix"))
        }

        @Test
        fun `should use configured project and module bindings for analysis`() = uiTest {
            // Scala should only be supported in connected mode
            openExistingProject("sample-scala")
            verifyCurrentFileShowsCard("EmptyCard")

            openFile("HelloProject.scala")
            verifyCurrentFileShowsCard("NotConnectedCard")

            bindProjectAndModuleInFileSettings("sample-scala-module", SCALA_PROJECT_KEY,SCALA_MODULE_PROJECT_KEY)
            // Wait for re-analysis to happen
            with(this) {
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
            verifyCurrentFileRuleDescriptionTabContains("Variables should not be self-assigned")

            openFile("mod/src/HelloModule.scala", "HelloModule.scala")

            verifyCurrentFileTabContainsMessages(
                "Found 1 issue in 1 file",
                "HelloModule.scala",
            )
            clickCurrentFileIssue("Add a nested comment explaining why this function is empty or complete the implementation.")
            verifyCurrentFileRuleDescriptionTabContains("Methods should not be empty")

            openFile("mod/src/Excluded.scala", "Excluded.scala")
            verifyCurrentFileTabContainsMessages(
                "No analysis done on the current opened file",
                "This file is not automatically analyzed",
            )
            enableConnectedModeFromCurrentFilePanel(SCALA_PROJECT_KEY, false, "Orchestrator")
        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SharedConfigTests : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/shared-connected-mode-java-issue.xml"))
            ORCHESTRATOR.server.provisionProject(SHARED_CONNECTED_MODE_KEY, "Shared Connected Mode")
            ORCHESTRATOR.server.associateProjectToQualityProfile(
                SHARED_CONNECTED_MODE_KEY,
                "java",
                "SonarLint IT ConnectedMode Java Issue"
            )

            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/shared-connected-mode-java-module-issue.xml"))
            ORCHESTRATOR.server.provisionProject(SHARED_CONNECTED_MODE_MODULE_KEY, "Shared Connected Mode Module")
            ORCHESTRATOR.server.associateProjectToQualityProfile(SHARED_CONNECTED_MODE_MODULE_KEY, "java", "SonarLint IT ConnectedMode Java Module Issue")
        }

        @Test
        fun `should export then import connected mode configuration`() = uiTest {
            openExistingProject("shared-connected-mode")
            bindProjectAndModuleInFileSettings("shared-connected-mode-module", SHARED_CONNECTED_MODE_KEY, SHARED_CONNECTED_MODE_MODULE_KEY)
            shareConfiguration()
            enableConnectedModeFromCurrentFilePanel(SHARED_CONNECTED_MODE_KEY, false, "Orchestrator")
            clearConnections()
            closeProject()
            openExistingProject("shared-connected-mode", copyProjectFiles = false)
            importConfiguration(tokenValue)
        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class NewConnectionsFromToolWindowTests : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            restoreSonarCloudProfile(adminSonarCloudWsClient, "java-sonarlint-with-issue.xml")
            provisionSonarCloudProfile(adminSonarCloudWsClient, "SLI Java Issues", SONARCLOUD_ISSUE_PROJECT_KEY)
            associateSonarCloudProjectToQualityProfile(
                adminSonarCloudWsClient,
                "java",
                SONARCLOUD_ISSUE_PROJECT_KEY,
                "SonarLint IT Java Issue"
            )

            analyzeSonarCloudWithMaven(adminSonarCloudWsClient, SONARCLOUD_ISSUE_PROJECT_KEY, "sli-java-issues", sonarCloudToken)
        }

        @Test
        fun `should create new connection from tool window and autofill created connection`() = uiTest {
            addSonarCloudConnection(sonarCloudToken, "Initial connection")
            openExistingProject("sli-java-issues")
            enableConnectedModeFromCurrentFilePanel(SONARCLOUD_ISSUE_PROJECT_KEY, true, "Initial connection")
            bindProjectFromToolWindow(SONARCLOUD_ISSUE_PROJECT_KEY, "New Connection Name", sonarCloudToken, "Initial connection")
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SampleJavaIssuesTests : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-issue.xml"))
            ORCHESTRATOR.server.provisionProject(ISSUE_PROJECT_KEY, "SLI Java Issues")
            ORCHESTRATOR.server.associateProjectToQualityProfile(
                ISSUE_PROJECT_KEY,
                "java",
                "SonarLint IT Java Issue"
            )
            // Build and analyze project to raise issue
            executeBuildWithMaven("projects/sli-java-issues/pom.xml", ORCHESTRATOR)
            firstIssueKey = getFirstIssueKey(adminWsClient, ISSUE_PROJECT_KEY)

            restoreSonarCloudProfile(adminSonarCloudWsClient, "java-sonarlint-with-issue.xml")
            provisionSonarCloudProfile(adminSonarCloudWsClient, "SLI Java Issues", SONARCLOUD_ISSUE_PROJECT_KEY)
            associateSonarCloudProjectToQualityProfile(
                adminSonarCloudWsClient,
                "java",
                SONARCLOUD_ISSUE_PROJECT_KEY,
                "SonarLint IT Java Issue"
            )

            analyzeSonarCloudWithMaven(adminSonarCloudWsClient, SONARCLOUD_ISSUE_PROJECT_KEY, "sli-java-issues", sonarCloudToken)
            firstSCIssueKey = getFirstSCIssueKey(adminSonarCloudWsClient)
        }

        @Test
        fun `should create connection with sonarcloud and analyze issue`() = uiTest {
            addSonarCloudConnection(sonarCloudToken, "SonarCloud-IT")

            openExistingProject("sli-java-issues")
            enableConnectedModeFromCurrentFilePanel(SONARCLOUD_ISSUE_PROJECT_KEY, true, "SonarCloud-IT")
            openFile("src/main/java/foo/Foo.java", "Foo.java")
            verifyCurrentFileTabContainsMessages("Remove this empty class, write its code or make it an \"interface\".")

            openIssueReviewDialogFromList("Remove this empty class, write its code or make it an \"interface\".")
            changeStatusOnSonarCloudAndPressChange("Accepted")
            confirm()
            verifyIssueStatusWasSuccessfullyChanged()

            enableConnectedModeFromCurrentFilePanel(SONARCLOUD_ISSUE_PROJECT_KEY, false, "SonarCloud-IT")
        }

    }


}
