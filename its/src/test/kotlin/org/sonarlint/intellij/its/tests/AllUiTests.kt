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
import com.sonar.orchestrator.container.Edition
import com.sonar.orchestrator.http.HttpMethod
import com.sonar.orchestrator.junit5.OrchestratorExtension
import com.sonar.orchestrator.locator.FileLocation
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.DisabledIf
import org.junit.jupiter.api.extension.RegisterExtension
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.changeStatusAndPressChange
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.clickCurrentFileIssue
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.confirm
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.enableConnectedModeFromCurrentFilePanel
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.openIssueReviewDialogFromList
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyIssueStatusWasSuccessfullyChanged
import org.sonarlint.intellij.its.tests.domain.FiltersTests.Companion.setFocusOnNewCode
import org.sonarlint.intellij.its.tests.domain.FiltersTests.Companion.showResolvedIssues
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.Companion.bindRecentProject
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.Companion.createConnection
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.Companion.triggerOpenHotspotRequest
import org.sonarlint.intellij.its.tests.domain.OpenInIdeTests.Companion.verifyHotspotOpened
import org.sonarlint.intellij.its.tests.domain.ReportTabTests.Companion.analyzeAndVerifyReportTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.changeSecurityHotspotStatusAndPressChange
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.enableConnectedModeFromSecurityHotspotPanel
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.openSecurityHotspotReviewDialogFromList
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.verifySecurityHotspotStatusWasSuccessfullyChanged
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.verifySecurityHotspotTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.SecurityHotspotTabTests.Companion.verifySecurityHotspotTreeContainsMessages
import org.sonarlint.intellij.its.tests.domain.TaintVulnerabilityTests.Companion.enableConnectedModeFromTaintPanel
import org.sonarlint.intellij.its.tests.domain.TaintVulnerabilityTests.Companion.verifyTaintTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.defaultBuilderEnv
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.executeBuildWithMaven
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.executeBuildWithSonarScanner
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.generateToken
import org.sonarlint.intellij.its.utils.OrchestratorUtils.Companion.newAdminWsClientWithUser
import org.sonarlint.intellij.its.utils.ProjectBindingUtils.Companion.bindProjectAndModuleInFileSettings
import org.sonarlint.intellij.its.utils.SettingsUtils.Companion.clearConnections
import org.sonarlint.intellij.its.utils.SettingsUtils.Companion.clearConnectionsAndAddSonarQubeConnection
import org.sonarlint.intellij.its.utils.SettingsUtils.Companion.clickPowerSaveMode
import org.sonarlint.intellij.its.utils.TabUtils.Companion.verifyCurrentFileShowsCard
import org.sonarlint.intellij.its.utils.TabUtils.Companion.verifyRuleDescriptionTabContains
import org.sonarqube.ws.client.WsClient
import org.sonarqube.ws.client.issues.DoTransitionRequest
import org.sonarqube.ws.client.issues.SearchRequest
import org.sonarqube.ws.client.settings.SetRequest

@DisabledIf("isCLionOrGoLand")
class AllUiTests : BaseUiTest() {

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

        const val TAINT_VULNERABILITY_PROJECT_KEY = "sample-java-taint-vulnerability"
        const val SECURITY_HOTSPOT_PROJECT_KEY = "sample-java-hotspot"
        const val PROJECT_KEY = "sample-scala"
        const val MODULE_PROJECT_KEY = "sample-scala-mod"
        const val ISSUE_PROJECT_KEY = "sample-java-issues"

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
            token = generateToken(adminWsClient, "sonarlintUser")

            clearConnectionsAndAddSonarQubeConnection(remoteRobot, ORCHESTRATOR.server.url, token)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisabledIf("isCLionOrGoLand", disabledReason = "No Java security hotspots in CLion or GoLand")
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
            clearConnections(this)
            openExistingProject(this, "sample-java-hotspot", true)

            // Open In Ide Security Hotspot Test
            triggerOpenHotspotRequest(SECURITY_HOTSPOT_PROJECT_KEY, firstHotspotKey, ORCHESTRATOR.server.url)
            createConnection(this)
            bindRecentProject(this)
            verifyHotspotOpened(this)

            // Should Propose To Bind
            enableConnectedModeFromSecurityHotspotPanel(this, SECURITY_HOTSPOT_PROJECT_KEY, false)
            verifySecurityHotspotTabContainsMessages(this, "The project is not bound, please bind it to SonarQube 9.7+ or SonarCloud")

            // Review Security Hotspot Test
            enableConnectedModeFromSecurityHotspotPanel(this, SECURITY_HOTSPOT_PROJECT_KEY, true)
            verifySecurityHotspotTreeContainsMessages(
                this,
                "Make sure using this hardcoded IP address is safe here."
            )
            openSecurityHotspotReviewDialogFromList(this, "Make sure using this hardcoded IP address is safe here.")
            changeSecurityHotspotStatusAndPressChange(this, "Fixed")
            verifySecurityHotspotStatusWasSuccessfullyChanged(this)
        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisabledIf("isCLionOrGoLand")
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
            openExistingProject(this, "sample-scala", true)
            verifyCurrentFileShowsCard(this, "EmptyCard")

            openFile(this, "HelloProject.scala")
            verifyCurrentFileShowsCard(this, "NotConnectedCard")

            bindProjectAndModuleInFileSettings()
            // Wait for re-analysis to happen
            with(this) {
                idea {
                    waitBackgroundTasksFinished()
                }
            }
            verifyCurrentFileShowsCard(this, "ConnectedCard")
            verifyCurrentFileTabContainsMessages(
                this,
                "Found 1 issue in 1 file",
                "HelloProject.scala",
            )
            clickCurrentFileIssue(this, "Remove or correct this useless self-assignment.")
            verifyRuleDescriptionTabContains(this, "Variables should not be self-assigned")

            openFile(this, "mod/src/HelloModule.scala", "HelloModule.scala")

            verifyCurrentFileTabContainsMessages(
                this,
                "Found 1 issue in 1 file",
                "HelloModule.scala",
            )
            clickCurrentFileIssue(this, "Add a nested comment explaining why this function is empty or complete the implementation.")
            verifyRuleDescriptionTabContains(this, "Methods should not be empty")

            openFile(this, "mod/src/Excluded.scala", "Excluded.scala")
            verifyCurrentFileTabContainsMessages(
                this,
                "No analysis done on the current opened file",
                "This file is not automatically analyzed",
            )
        }

    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisabledIf("isCLionOrGoLand", disabledReason = "No Java Issues in CLion or GoLand")
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
        }

        @Test
        fun should_analyze_issue_then_should_review_issue_then_should_not_analyze_with_power_save_mode() = uiTest {
            openExistingProject(this, "sample-java-issues")

            // Issue Analysis Test
            enableConnectedModeFromCurrentFilePanel(this, ISSUE_PROJECT_KEY, true)
            openFile(this, "src/main/java/foo/Foo.java", "Foo.java")
            verifyCurrentFileTabContainsMessages(this, "Move this trailing comment on the previous empty line.")

            // Issue Reviewing Test
            openIssueReviewDialogFromList(this, "Move this trailing comment on the previous empty line.")
            changeStatusAndPressChange(this, "False Positive")
            confirm(this)
            verifyIssueStatusWasSuccessfullyChanged(this)
            showResolvedIssues(this)
            verifyCurrentFileTabContainsMessages(this, "Move this trailing comment on the previous empty line.")
            showResolvedIssues(this)

            // Power Save Mode Test
            clickPowerSaveMode(this)
            openFile(this, "src/main/java/foo/Bar.java", "Bar.java")
            verifyCurrentFileTabContainsMessages(
                this,
                "No analysis done on the current opened file",
                "This file is not automatically analyzed because power save mode is enabled"
            )
            clickPowerSaveMode(this)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisabledIf("isCLionOrGoLand", disabledReason = "No Java Issues in CLion or GoLand")
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
            openExistingProject(this, "sample-java-taint-vulnerability", true)

            // Focus On New Code Test
            enableConnectedModeFromTaintPanel(this, TAINT_VULNERABILITY_PROJECT_KEY, true)
            openFile(this, "src/main/java/foo/FileWithSink.java", "FileWithSink.java")
            setFocusOnNewCode(this)
            analyzeAndVerifyReportTabContainsMessages(
                this,
                "Found 2 new issues in 1 file from last 1 days",
                "No older issues",
                "Found 1 new Security Hotspot in 1 file from last 1 days",
                "No older Security Hotspots"
            )
            verifyTaintTabContainsMessages(
                this,
                "Found 1 new issue in 1 file from last 1 days",
                "FileWithSink.java",
                "Change this code to not construct SQL queries directly from user-controlled data.",
                "No older issues"
            )
            verifySecurityHotspotTabContainsMessages(
                this,
                "Found 1 new Security Hotspot in 1 file from last 1 days",
                "No older Security Hotspots"
            )
            verifyCurrentFileTabContainsMessages(
                this,
                "Found 2 new issues in 1 file from last 1 days",
                "No older issues"
            )
            setFocusOnNewCode(this)

            // Taint Vulnerability Test
            verifyTaintTabContainsMessages(
                this,
                "Found 1 issue in 1 file",
                "FileWithSink.java",
                "Change this code to not construct SQL queries directly from user-controlled data."
            )
            enableConnectedModeFromTaintPanel(this, TAINT_VULNERABILITY_PROJECT_KEY, false)
            verifyTaintTabContainsMessages(this, "The project is not bound to SonarQube/SonarCloud")
        }

    }

}