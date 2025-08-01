package org.sonarlint.intellij.its.tests

import com.sonar.orchestrator.container.Edition
import com.sonar.orchestrator.junit5.OrchestratorExtension
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
            .setEdition(Edition.ENTERPRISE) // todo SCA not enabled?
            .activateLicense()
            .addBundledPluginToKeep("sonar-java")
            .addBundledPluginToKeep("sonar-security")
            .addBundledPluginToKeep("sonar-php")
            .addBundledPluginToKeep("sonar-python")
            .addBundledPluginToKeep("sonar-kotlin")
            .addBundledPluginToKeep("sonar-go")
            .build()

        private lateinit var adminWsClient: WsClient
//        private lateinit var adminSonarCloudWsClient: WsClient // todo no clouds today

        lateinit var tokenName: String
        lateinit var tokenValue: String

        private fun projectKey(key: String): String {
            val randomPositiveInt = Random.nextInt(Int.MAX_VALUE)
            return "sli-its-$key-$randomPositiveInt"
        }

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
