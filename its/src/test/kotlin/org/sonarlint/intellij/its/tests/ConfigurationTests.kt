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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.extension.RegisterExtension
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.idea
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
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.closeProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.defaultBuilderEnv
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.executeBuildWithMaven
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.executeBuildWithSonarScanner
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.generateTokenNameAndValue
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.newAdminWsClientWithUser
import org.sonarlint.intellij.its.utils.ProjectBindingUtils.Companion.bindProjectAndModuleInFileSettings
import org.sonarlint.intellij.its.utils.SettingsUtils.Companion.addSonarCloudConnection
import org.sonarlint.intellij.its.utils.SettingsUtils.Companion.clearConnections
import org.sonarlint.intellij.its.utils.SettingsUtils.Companion.clearConnectionsAndAddSonarQubeConnection
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.SONARCLOUD_STAGING_URL
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.analyzeSonarCloudWithMaven
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.associateSonarCloudProjectToQualityProfile
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.cleanupProjects
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.newAdminSonarCloudWsClientWithUser
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.provisionSonarCloudProfile
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.restoreSonarCloudProfile
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
            .build()

        private lateinit var adminWsClient: WsClient
        private lateinit var adminSonarCloudWsClient: WsClient

        const val PROJECT_KEY = "sample-scala"
        const val MODULE_PROJECT_KEY = "sample-scala-mod"
        const val ISSUE_PROJECT_KEY = "sli-java-issues"
        const val SHARED_CONNECTED_MODE_KEY = "shared-connected-mode"
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

        private fun getFirstIssueKey(client: WsClient): String? {
            val searchRequest = SearchRequest()
            searchRequest.projects = listOf(ISSUE_PROJECT_KEY)
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

    @Disabled("Flaky - excluded files from server are not always taken into account")
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SampleScalaTests : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/scala-sonarlint-self-assignment.xml"))
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/scala-sonarlint-empty-method.xml"))

            ORCHESTRATOR.server.provisionProject(PROJECT_KEY, "Sample Scala")
            ORCHESTRATOR.server.associateProjectToQualityProfile(PROJECT_KEY, "scala", "SonarLint IT Scala")
            ORCHESTRATOR.server.provisionProject(MODULE_PROJECT_KEY, "Sample Scala Module")
            ORCHESTRATOR.server.associateProjectToQualityProfile(MODULE_PROJECT_KEY, "scala", "SonarLint IT Scala Module")

            val excludeFileRequest = SetRequest()
            excludeFileRequest.key = "sonar.exclusions"
            excludeFileRequest.component = MODULE_PROJECT_KEY
            excludeFileRequest.values = listOf("src/Excluded.scala")
            adminWsClient.settings().set(excludeFileRequest)

            executeBuildWithSonarScanner("projects/sample-scala/", ORCHESTRATOR, PROJECT_KEY)
            executeBuildWithSonarScanner("projects/sample-scala/mod/", ORCHESTRATOR, MODULE_PROJECT_KEY)

            val searchRequest = SearchRequest()
            searchRequest.s = "FILE_LINE"
            searchRequest.projects = listOf(MODULE_PROJECT_KEY)
            val response = adminWsClient.issues().search(searchRequest)
            val firstIssueKey = response.issuesList[0].key
            adminWsClient.issues().doTransition(DoTransitionRequest().setIssue(firstIssueKey).setTransition("wontfix"))
        }

        @Test
        fun should_use_configured_project_and_module_bindings_for_analysis() = uiTest {
            // Scala should only be supported in connected mode
            openExistingProject("sample-scala", true)
            verifyCurrentFileShowsCard("EmptyCard")

            openFile("HelloProject.scala")
            verifyCurrentFileShowsCard("NotConnectedCard")

            bindProjectAndModuleInFileSettings()
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
            enableConnectedModeFromCurrentFilePanel(PROJECT_KEY, false, "Orchestrator")
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
            // Build and analyze project to raise issue
            executeBuildWithMaven("projects/shared-connected-mode/pom.xml", ORCHESTRATOR)
            firstIssueKey = getFirstIssueKey(adminWsClient)
        }

        @Test
        fun should_export_then_import_connected_mode_configuration() = uiTest {
            openExistingProject("shared-connected-mode")
            enableConnectedModeFromCurrentFilePanel(SHARED_CONNECTED_MODE_KEY, true, "Orchestrator")
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
            firstIssueKey = getFirstIssueKey(adminWsClient)

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
        fun should_create_connection_with_sonarcloud_and_analyze_issue() = uiTest {
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
