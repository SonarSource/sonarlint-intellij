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
import com.sonar.orchestrator.http.HttpMethod
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
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.enableConnectedModeFromCurrentFilePanel
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.ReportTabTests.Companion.analyzeAndVerifyReportTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.TaintVulnerabilityTests.Companion.enableConnectedModeFromTaintPanel
import org.sonarlint.intellij.its.tests.domain.TaintVulnerabilityTests.Companion.verifyTaintTabContainsMessages
import org.sonarlint.intellij.its.utils.FiltersUtils.resetFocusOnNewCode
import org.sonarlint.intellij.its.utils.FiltersUtils.setFocusOnNewCode
import org.sonarlint.intellij.its.utils.OpeningUtils.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.openFile
import org.sonarlint.intellij.its.utils.OrchestratorUtils.defaultBuilderEnv
import org.sonarlint.intellij.its.utils.OrchestratorUtils.executeBuildWithMaven
import org.sonarlint.intellij.its.utils.OrchestratorUtils.generateTokenNameAndValue
import org.sonarlint.intellij.its.utils.OrchestratorUtils.newAdminWsClientWithUser
import org.sonarlint.intellij.its.utils.SettingsUtils.clearConnectionsAndAddSonarQubeConnection
import org.sonarlint.intellij.its.utils.SonarCloudUtils.SONARCLOUD_STAGING_URL
import org.sonarlint.intellij.its.utils.SonarCloudUtils.cleanupProjects
import org.sonarlint.intellij.its.utils.SonarCloudUtils.newAdminSonarCloudWsClientWithUser
import org.sonarqube.ws.client.WsClient
import org.sonarqube.ws.client.issues.SearchRequest
import org.sonarqube.ws.client.usertokens.GenerateRequest
import org.sonarqube.ws.client.usertokens.RevokeRequest

@Tag("ConnectedAnalysisTests")
@EnabledIf("isIdeaCommunity")
class ConnectedAnalysisTests : BaseUiTest() {

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
            .addBundledPluginToKeep("sonar-swift")
            .addBundledPluginToKeep("sonar-kotlin")
            .addBundledPluginToKeep("sonar-go")
            .build()

        private lateinit var adminWsClient: WsClient
        private lateinit var adminSonarCloudWsClient: WsClient

        const val TAINT_VULNERABILITY_PROJECT_KEY = "sample-java-taint-vulnerability"
        const val ISSUE_PROJECT_KEY = "sli-java-issues"
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
            openExistingProject("sample-java-taint-vulnerability")

            // Focus On New Code Test
            enableConnectedModeFromTaintPanel(TAINT_VULNERABILITY_PROJECT_KEY, true, "Orchestrator")
            openFile("src/main/java/foo/FileWithSink.java", "FileWithSink.java")
            setFocusOnNewCode()
            analyzeAndVerifyReportTabContainsMessages(
                "No new issues from last 1 days",
                "No new Security Hotspots from last 1 days",
                "Found 2 older issues",
                "Found 1 older Security Hotspot"
            )
            verifyCurrentFileTabContainsMessages(
                "Found 2 older issues",
                "Found 1 older Security Hotspot",
                "Found 1 older Taint Vulnerability"
            )
            resetFocusOnNewCode()

            verifyCurrentFileTabContainsMessages(
                "Found 1 Taint Vulnerability",
                "Change this code to not construct SQL queries directly from user-controlled data."
            )
            enableConnectedModeFromTaintPanel(TAINT_VULNERABILITY_PROJECT_KEY, false, "Orchestrator")
            verifyTaintTabContainsMessages("The project is not bound to SonarQube (Server, Cloud)")
        }

        @Test
        fun should_analyze_swift() = uiTest {
            openExistingProject("sample-swift")

            enableConnectedModeFromCurrentFilePanel(TAINT_VULNERABILITY_PROJECT_KEY, true, "Orchestrator")

            openFile("file.swift")
            verifyCurrentFileTabContainsMessages(
                "Found 1 issue",
                "Use \"try\" or \"try?\" here instead; \"try!\" disables error propagation."
            )
            enableConnectedModeFromCurrentFilePanel(TAINT_VULNERABILITY_PROJECT_KEY, false, "Orchestrator")
        }

    }

}
