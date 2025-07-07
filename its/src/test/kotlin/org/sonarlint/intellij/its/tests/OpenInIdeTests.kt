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
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.changeStatusOnSonarQubeAndPressChange
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.confirm
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.enableConnectedModeFromCurrentFilePanel
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.openIssueReviewDialogFromList
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyIssueStatusWasSuccessfullyChanged
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.acceptNewAutomatedConnection
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.acceptNewSCAutomatedConnection
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.createConnection
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.triggerOpenHotspotRequest
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.triggerOpenIssueRequest
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.triggerOpenSCIssueRequest
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.verifyHotspotOpened
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.verifyIssueOpened
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.changeSecurityHotspotStatusAndPressChange
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.enableConnectedModeFromSecurityHotspotPanel
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.openSecurityHotspotReviewDialogFromList
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.verifySecurityHotspotStatusWasSuccessfullyChanged
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.verifySecurityHotspotTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.verifySecurityHotspotTreeContainsMessages
import org.sonarlint.intellij.its.utils.FiltersUtils.showResolvedIssues
import org.sonarlint.intellij.its.utils.OpeningUtils.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.openFile
import org.sonarlint.intellij.its.utils.OrchestratorUtils.defaultBuilderEnv
import org.sonarlint.intellij.its.utils.OrchestratorUtils.executeBuildWithMaven
import org.sonarlint.intellij.its.utils.OrchestratorUtils.generateTokenNameAndValue
import org.sonarlint.intellij.its.utils.OrchestratorUtils.newAdminWsClientWithUser
import org.sonarlint.intellij.its.utils.SettingsUtils.clearConnections
import org.sonarlint.intellij.its.utils.SettingsUtils.clearConnectionsAndAddSonarQubeConnection
import org.sonarlint.intellij.its.utils.SonarCloudUtils.SONARCLOUD_ORGANIZATION
import org.sonarlint.intellij.its.utils.SonarCloudUtils.SONARCLOUD_STAGING_URL
import org.sonarlint.intellij.its.utils.SonarCloudUtils.analyzeSonarCloudWithMaven
import org.sonarlint.intellij.its.utils.SonarCloudUtils.associateSonarCloudProjectToQualityProfile
import org.sonarlint.intellij.its.utils.SonarCloudUtils.cleanupProjects
import org.sonarlint.intellij.its.utils.SonarCloudUtils.newAdminSonarCloudWsClientWithUser
import org.sonarlint.intellij.its.utils.SonarCloudUtils.provisionSonarCloudProfile
import org.sonarlint.intellij.its.utils.SonarCloudUtils.restoreSonarCloudProfile
import org.sonarqube.ws.client.WsClient
import org.sonarqube.ws.client.issues.SearchRequest
import org.sonarqube.ws.client.usertokens.GenerateRequest
import org.sonarqube.ws.client.usertokens.RevokeRequest

// In order to run these test change the url triggerOpenHotspotRequest to some other port than 64120 depending on number of IntelliJ instances
@Tag("OpenInIdeTests")
@EnabledIf("isIdeaCommunity")
class OpenInIdeTests : BaseUiTest() {

    companion object {
        @JvmField
        @RegisterExtension
        val ORCHESTRATOR: OrchestratorExtension = defaultBuilderEnv()
            .setEdition(Edition.DEVELOPER)
            .activateLicense()
            .addBundledPluginToKeep("sonar-java")
            .addBundledPluginToKeep("sonar-security")
            .addBundledPluginToKeep("sonar-php")
            .addBundledPluginToKeep("sonar-python")
            .addBundledPluginToKeep("sonar-kotlin")
            .build()

        private lateinit var adminWsClient: WsClient
        private lateinit var adminSonarCloudWsClient: WsClient

        const val SECURITY_HOTSPOT_PROJECT_KEY = "sample-java-hotspot"
        const val ISSUE_PROJECT_KEY = "sli-java-issues"
        val SONARCLOUD_ISSUE_PROJECT_KEY = projectKey(ISSUE_PROJECT_KEY)

        private var firstHotspotKey: String? = null
        private var firstIssueKey: String? = null
        private var firstSCIssueKey: String? = null
        lateinit var tokenName: String
        lateinit var tokenValue: String
        lateinit var sonarCloudToken: String
        private val sonarCloudTokenName = "SLCORE-IT-${System.currentTimeMillis()}"

        private fun projectKey(key: String): String {
            val randomPositiveInt = Random.nextInt(Int.MAX_VALUE)
            return "sli-its-$key-$randomPositiveInt"
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
    @Disabled("Flaky test - after creating the binding, the Security Hotspot is not always detected as from SQ")
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
        fun should_open_in_ide_security_hotspot_then_should_propose_to_bind_then_should_review_security_hotspot() = uiTest {
            clearConnections()
            openExistingProject("sample-java-hotspot")

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
            enableConnectedModeFromSecurityHotspotPanel(SECURITY_HOTSPOT_PROJECT_KEY, false, "Orchestrator")
        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SampleJavaIssuesTests : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            ORCHESTRATOR.server.restoreProfile(FileLocation.ofClasspath("/java-sonarlint-with-issue.xml"))
            ORCHESTRATOR.server.provisionProject(ISSUE_PROJECT_KEY, ISSUE_PROJECT_KEY)
            ORCHESTRATOR.server.associateProjectToQualityProfile(
                ISSUE_PROJECT_KEY,
                "java",
                "SonarLint IT Java Issue"
            )
            // Build and analyze project to raise issue
            executeBuildWithMaven("projects/sli-java-issues/pom.xml", ORCHESTRATOR)
            firstIssueKey = getFirstIssueKey(adminWsClient)

            restoreSonarCloudProfile(adminSonarCloudWsClient, "java-sonarlint-with-issue.xml")
            provisionSonarCloudProfile(adminSonarCloudWsClient, ISSUE_PROJECT_KEY, SONARCLOUD_ISSUE_PROJECT_KEY)
            associateSonarCloudProjectToQualityProfile(
                adminSonarCloudWsClient,
                "java",
                SONARCLOUD_ISSUE_PROJECT_KEY,
                "SonarLint IT Java Issue"
            )

            analyzeSonarCloudWithMaven(adminSonarCloudWsClient, SONARCLOUD_ISSUE_PROJECT_KEY, "sli-java-issues", sonarCloudToken)

            firstSCIssueKey = getFirstSCIssueKey(adminSonarCloudWsClient)
        }

        @Disabled("Doesn't find the project")
        @Test
        fun click_open_in_ide_SC_issue_then_should_automatically_create_connection_then_should_automatically_bind() = uiTest {
            clearConnections()
            openExistingProject(ISSUE_PROJECT_KEY)
            triggerOpenSCIssueRequest(
                SONARCLOUD_ISSUE_PROJECT_KEY,
                firstSCIssueKey,
                SONARCLOUD_STAGING_URL,
                "master",
                sonarCloudTokenName,
                sonarCloudToken,
                SONARCLOUD_ORGANIZATION
            )
            acceptNewSCAutomatedConnection()
            verifyIssueOpened()
            enableConnectedModeFromCurrentFilePanel(SONARCLOUD_ISSUE_PROJECT_KEY, false, "sonarlint-it")
        }

        @Test
        fun should_analyze_issue_then_should_review_issue() = uiTest {
            openExistingProject(ISSUE_PROJECT_KEY)

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
        }

        @Test
        fun click_open_in_ide_issue_then_should_manually_create_connection_then_should_automatically_bind() = uiTest {
            clearConnections()
            openExistingProject("sli-java-issues")
            triggerOpenIssueRequest(ISSUE_PROJECT_KEY, firstIssueKey, ORCHESTRATOR.server.url, "main")
            createConnection(tokenValue)
            verifyIssueOpened()
        }

        @Test
        fun click_open_in_ide_issue_then_should_automatically_create_connection_then_should_automatically_bind() = uiTest {
            clearConnections()
            openExistingProject(ISSUE_PROJECT_KEY)
            triggerOpenIssueRequest(ISSUE_PROJECT_KEY, firstIssueKey, ORCHESTRATOR.server.url, "main", tokenName, tokenValue)
            acceptNewAutomatedConnection()
            verifyIssueOpened()
        }

    }

}
