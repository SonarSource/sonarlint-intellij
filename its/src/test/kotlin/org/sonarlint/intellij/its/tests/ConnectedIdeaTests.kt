/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import com.sonar.orchestrator.http.HttpMethod
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
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.changeStatusOnSonarQubeAndPressChange
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.clickCurrentFileIssue
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.confirm
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.enableConnectedModeFromCurrentFilePanel
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.openIssueReviewDialogFromList
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileRuleDescriptionTabContains
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileShowsCard
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyIssueStatusWasSuccessfullyChanged
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.Companion.acceptNewAutomatedConnection
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.Companion.createConnection
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.Companion.triggerOpenHotspotRequest
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.Companion.triggerOpenIssueRequest
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.Companion.verifyHotspotOpened
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.Companion.verifyIssueOpened
import org.sonarlint.intellij.its.tests.domain.ReportTabTests.Companion.analyzeAndVerifyReportTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.changeSecurityHotspotStatusAndPressChange
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.enableConnectedModeFromSecurityHotspotPanel
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.openSecurityHotspotReviewDialogFromList
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.verifySecurityHotspotStatusWasSuccessfullyChanged
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.verifySecurityHotspotTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.verifySecurityHotspotTreeContainsMessages
import org.sonarlint.intellij.its.tests.domain.SharedConfigurationTests.Companion.importConfiguration
import org.sonarlint.intellij.its.tests.domain.SharedConfigurationTests.Companion.shareConfiguration
import org.sonarlint.intellij.its.tests.domain.TaintVulnerabilityTests.Companion.enableConnectedModeFromTaintPanel
import org.sonarlint.intellij.its.tests.domain.TaintVulnerabilityTests.Companion.verifyTaintTabContainsMessages
import org.sonarlint.intellij.its.utils.FiltersUtils.Companion.resetFocusOnNewCode
import org.sonarlint.intellij.its.utils.FiltersUtils.Companion.setFocusOnNewCode
import org.sonarlint.intellij.its.utils.FiltersUtils.Companion.showResolvedIssues
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
import org.sonarlint.intellij.its.utils.SettingsUtils.Companion.clickPowerSaveMode
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.SONARCLOUD_STAGING_URL
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.analyzeSonarCloudWithMaven
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.associateSonarCloudProjectToQualityProfile
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.newAdminSonarCloudWsClientWithUser
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.provisionSonarCloudProfile
import org.sonarlint.intellij.its.utils.SonarCloudUtils.Companion.restoreSonarCloudProfile
import org.sonarqube.ws.client.WsClient
import org.sonarqube.ws.client.issues.DoTransitionRequest
import org.sonarqube.ws.client.issues.SearchRequest
import org.sonarqube.ws.client.settings.SetRequest
import org.sonarqube.ws.client.usertokens.GenerateRequest
import org.sonarqube.ws.client.usertokens.RevokeRequest

// In order to run these test change the url triggerOpenHotspotRequest to some other port than 64120 depending on number of IntelliJ instances
@EnabledIf("isIdeaCommunity")
class ConnectedIdeaTests : BaseUiTest() {

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
            .addBundledPluginToKeep("sonar-swift")
            .build()

        private lateinit var adminWsClient: WsClient
        private lateinit var adminSonarCloudWsClient: WsClient

        const val TAINT_VULNERABILITY_PROJECT_KEY = "sample-java-taint-vulnerability"
        const val SECURITY_HOTSPOT_PROJECT_KEY = "sample-java-hotspot"
        const val PROJECT_KEY = "sample-scala"
        const val MODULE_PROJECT_KEY = "sample-scala-mod"
        const val ISSUE_PROJECT_KEY = "sample-java-issues"
        const val SHARED_CONNECTED_MODE_KEY = "shared-connected-mode"
        val SONARCLOUD_ISSUE_PROJECT_KEY = projectKey(ISSUE_PROJECT_KEY)

        private var firstHotspotKey: String? = null
        private var firstIssueKey: String? = null
        lateinit var tokenName: String
        lateinit var tokenValue: String
        lateinit var sonarCloudToken: String
        private val sonarCloudTokenName = "SLCORE-IT-${System.currentTimeMillis()}"

        private fun projectKey(key: String): String {
            val randomPositiveInt = Random.nextInt(Int.MAX_VALUE)
            return "sonarlint-its-$key-$randomPositiveInt"
        }

        private fun getFirstHotspotKey(client: WsClient): String? {
            val searchRequest = org.sonarqube.ws.client.hotspots.SearchRequest()
            searchRequest.projectKey = SECURITY_HOTSPOT_PROJECT_KEY
            val searchResults = client.hotspots().search(searchRequest)
            val hotspot = searchResults.hotspotsList[0]
            return hotspot.key
        }

        private fun getFirstIssueKey(client: WsClient): String? {
            val searchRequest = SearchRequest()
            searchRequest.projects = listOf(ISSUE_PROJECT_KEY)
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
            adminSonarCloudWsClient.userTokens().revoke(RevokeRequest().setName(sonarCloudTokenName))
        }
    }

    @Tag("Suite1")
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SampleJavaHotspotTests : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-hotspot.xml"))

            ORCHESTRATOR.server.provisionProject(SECURITY_HOTSPOT_PROJECT_KEY, "Sample Java")
            ORCHESTRATOR.server.associateProjectToQualityProfile(SECURITY_HOTSPOT_PROJECT_KEY, "java", "SonarLint IT Java Hotspot")

            // Build and analyze project to raise hotspot
            executeBuildWithMaven("projects/sample-java-hotspot/pom.xml", ORCHESTRATOR)

            firstHotspotKey = getFirstHotspotKey(adminWsClient)
        }

        @Test
        @Disabled("Flaky test - after creating the binding, the Security Hotspot is not always detected as from SQ")
        fun should_open_in_ide_security_hotspot_then_should_propose_to_bind_then_should_review_security_hotspot() = uiTest {
            clearConnections()
            openExistingProject("sample-java-hotspot", true)

            // Open In Ide Security Hotspot Test
            triggerOpenHotspotRequest(SECURITY_HOTSPOT_PROJECT_KEY, firstHotspotKey, ORCHESTRATOR.server.url)
            createConnection(tokenValue)
            verifyHotspotOpened()

            // Should Propose To Bind
            enableConnectedModeFromSecurityHotspotPanel(SECURITY_HOTSPOT_PROJECT_KEY, false, "Orchestrator")
            verifySecurityHotspotTabContainsMessages("The project is not bound, please bind it to SonarQube 9.7+ or SonarCloud")

            // Review Security Hotspot Test
            enableConnectedModeFromSecurityHotspotPanel(SECURITY_HOTSPOT_PROJECT_KEY, true, "Orchestrator")
            verifySecurityHotspotTreeContainsMessages("Make sure using this hardcoded IP address is safe here.")
            openSecurityHotspotReviewDialogFromList("Make sure using this hardcoded IP address is safe here.")
            changeSecurityHotspotStatusAndPressChange("Fixed")
            verifySecurityHotspotStatusWasSuccessfullyChanged()
        }

    }

    @Tag("Suite1")
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
        }

    }

    @Tag("Suite1")
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

    @Tag("Suite2")
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SampleJavaIssuesTests : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-issue.xml"))
            ORCHESTRATOR.server.provisionProject(ISSUE_PROJECT_KEY, "Sample Java Issues")
            ORCHESTRATOR.server.associateProjectToQualityProfile(
                ISSUE_PROJECT_KEY,
                "java",
                "SonarLint IT Java Issue"
            )
            // Build and analyze project to raise issue
            executeBuildWithMaven("projects/sample-java-issues/pom.xml", ORCHESTRATOR)
            firstIssueKey = getFirstIssueKey(adminWsClient)

            restoreSonarCloudProfile(adminSonarCloudWsClient, "java-sonarlint-with-issue.xml")
            provisionSonarCloudProfile(adminSonarCloudWsClient, "Sample Java Issues", SONARCLOUD_ISSUE_PROJECT_KEY)
            associateSonarCloudProjectToQualityProfile(
                adminSonarCloudWsClient,
                "java",
                SONARCLOUD_ISSUE_PROJECT_KEY,
                "SonarLint IT Java Issue"
            )
            analyzeSonarCloudWithMaven(adminSonarCloudWsClient, SONARCLOUD_ISSUE_PROJECT_KEY, "sample-java-issues", sonarCloudToken)
        }

        @Test
        fun should_analyze_issue_then_should_review_issue_then_should_not_analyze_with_power_save_mode() = uiTest {
            openExistingProject("sample-java-issues")

            // Issue Analysis Test
            enableConnectedModeFromCurrentFilePanel(ISSUE_PROJECT_KEY, true, "Orchestrator")
            openFile("src/main/java/foo/Foo.java", "Foo.java")
            verifyCurrentFileTabContainsMessages("Remove this empty class, write its code or make it an \"interface\".")

            // Issue Reviewing Test
            openIssueReviewDialogFromList("Remove this empty class, write its code or make it an \"interface\".")
            changeStatusOnSonarQubeAndPressChange("False Positive")
            confirm()
            verifyIssueStatusWasSuccessfullyChanged()
            showResolvedIssues()
            verifyCurrentFileTabContainsMessages("Remove this empty class, write its code or make it an \"interface\".")
            showResolvedIssues()

            // Power Save Mode Test
            clickPowerSaveMode()
            openFile("src/main/java/foo/Bar.java", "Bar.java")
            verifyCurrentFileTabContainsMessages("This file is not automatically analyzed because power save mode is enabled")
            clickPowerSaveMode()
        }

        @Test
        fun click_open_in_ide_issue_then_should_manually_create_connection_then_should_automatically_bind() = uiTest {
            clearConnections()
            openExistingProject("sample-java-issues")
            triggerOpenIssueRequest(ISSUE_PROJECT_KEY, firstIssueKey, ORCHESTRATOR.server.url, "main")
            createConnection(tokenValue)
            verifyIssueOpened()
        }

        @Test
        fun click_open_in_ide_issue_then_should_automatically_create_connection_then_should_automatically_bind() = uiTest {
            clearConnections()
            openExistingProject("sample-java-issues")
            triggerOpenIssueRequest(ISSUE_PROJECT_KEY, firstIssueKey, ORCHESTRATOR.server.url, "main", tokenName, tokenValue)
            acceptNewAutomatedConnection()
            verifyIssueOpened()
        }

        @Test
        fun should_create_connection_with_sonarcloud_and_analyze_issue() = uiTest {
            addSonarCloudConnection(sonarCloudToken, "SonarCloud-IT")

            openExistingProject("sample-java-issues")
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

    @Tag("Suite1")
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SampleJavaTaintVulnerabilityTests : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/java-taint-hotspot-issue.xml"))

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
            executeBuildWithMaven("projects/sample-java-taint-vulnerability/pom.xml", ORCHESTRATOR)

            // Analyze a second time for the measure to be returned by the web API
            executeBuildWithMaven("projects/sample-java-taint-vulnerability/pom.xml", ORCHESTRATOR)
        }

        @Test
        fun should_focus_on_new_code_in_each_tabs_then_should_find_taint_vulnerability_in_connected_mode() = uiTest {
            openExistingProject("sample-java-taint-vulnerability", true)

            // Focus On New Code Test
            enableConnectedModeFromTaintPanel(TAINT_VULNERABILITY_PROJECT_KEY, true, "Orchestrator")
            openFile("src/main/java/foo/FileWithSink.java", "FileWithSink.java")
            setFocusOnNewCode()
            analyzeAndVerifyReportTabContainsMessages(
                "No new issues from last 1 days",
                "No new Security Hotspots from last 1 days",
                "Found 2 older issues in 1 file",
                "Found 1 older Security Hotspot in 1 file"
            )
            verifyTaintTabContainsMessages(
                "No new issues from last 1 days",
                "Found 1 older issue in 1 file"
            )
            verifySecurityHotspotTabContainsMessages(
                "No new Security Hotspots from last 1 days",
                "Found 1 older Security Hotspot in 1 file"
            )
            verifyCurrentFileTabContainsMessages(
                "No new issues from last 1 days",
                "Found 2 older issues in 1 file",
            )
            resetFocusOnNewCode()

            // Taint Vulnerability Test
            verifyTaintTabContainsMessages(
                "Found 1 issue in 1 file",
                "FileWithSink.java",
                "Change this code to not construct SQL queries directly from user-controlled data."
            )
            enableConnectedModeFromTaintPanel(TAINT_VULNERABILITY_PROJECT_KEY, false, "Orchestrator")
            verifyTaintTabContainsMessages("The project is not bound to SonarCloud/SonarQube")
        }

        @Test
        fun should_analyze_swift() = uiTest {
            openExistingProject("sample-swift")

            enableConnectedModeFromCurrentFilePanel(TAINT_VULNERABILITY_PROJECT_KEY, true, "Orchestrator")

            openFile("file.swift")
            verifyCurrentFileTabContainsMessages(
                "Found 1 issue in 1 file",
                "file.swift",
                "Use \"try\" or \"try?\" here instead; \"try!\" disables error propagation."
            )
        }

    }

}
