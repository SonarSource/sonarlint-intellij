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
import org.sonarlint.intellij.its.tests.domain.DependencyRisksTabTests.Companion.verifyDependencyRisksTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.openExistingProject
import org.sonarlint.intellij.its.utils.OrchestratorUtils.defaultBuilderEnv
import org.sonarlint.intellij.its.utils.OrchestratorUtils.executeBuildWithMaven
import org.sonarlint.intellij.its.utils.OrchestratorUtils.generateTokenNameAndValue
import org.sonarlint.intellij.its.utils.OrchestratorUtils.newAdminWsClientWithUser
import org.sonarlint.intellij.its.utils.SettingsUtils.clearConnectionsAndAddSonarQubeConnection
import org.sonarqube.ws.client.WsClient

private const val SCA_PROJECT_KEY = "sample-sca"

@Tag("ScaTests")
@EnabledIf("isIdeaCommunity")
class ScaTests : BaseUiTest() {

    companion object {
        @JvmField
        @RegisterExtension
        val ORCHESTRATOR: OrchestratorExtension = defaultBuilderEnv()
            .setEdition(Edition.ENTERPRISE)
            .setServerProperty("sonar.sca.enabled", "true")
            .activateLicense()
            .addBundledPluginToKeep("sonar-java")
            .addBundledPluginToKeep("sonar-security")
            .addBundledPluginToKeep("sonar-php")
            .addBundledPluginToKeep("sonar-python")
            .addBundledPluginToKeep("sonar-kotlin")
            .addBundledPluginToKeep("sonar-go")
            .build()

        private lateinit var adminWsClient: WsClient

        lateinit var tokenName: String
        lateinit var tokenValue: String

        @JvmStatic
        @BeforeAll
        fun createSonarLintUser() {
            adminWsClient = newAdminWsClientWithUser(ORCHESTRATOR.server)
            val token = generateTokenNameAndValue(adminWsClient, "sonarlintUser")
            tokenName = token.first
            tokenValue = token.second

            clearConnectionsAndAddSonarQubeConnection(ORCHESTRATOR.server.url, tokenValue)
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {

        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SampleMavenDependencyRiskTests : BaseUiTest() {

        @BeforeAll
        fun initProfile() {
            ORCHESTRATOR.server.provisionProject(SCA_PROJECT_KEY, "SCA-test-project")

            // Analyze project to find dependency risks
            executeBuildWithMaven("projects/sample-sca/pom.xml", ORCHESTRATOR)
        }

        @Test
        fun `should show dependency risks`() {
            openExistingProject("sample-sca")

            // todo do it from Dependency risk panel
            enableConnectedModeFromCurrentFilePanel(SCA_PROJECT_KEY, true, "Orchestrator")

            verifyDependencyRisksTabContainsMessages("Found 1 dependency risk in SCA-test-project",
                "org.apache.tomcat.embed:tomcat-embed-core 9.0.7")
        }
    }
}
