/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.its.tests.flavor

import com.sonar.orchestrator.container.Edition
import com.sonar.orchestrator.junit5.OrchestratorExtension
import com.sonar.orchestrator.locator.FileLocation
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.enableConnectedModeFromCurrentFilePanel
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.utils.OpeningUtils.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.openFile
import org.sonarlint.intellij.its.utils.OrchestratorUtils.defaultBuilderEnv
import org.sonarlint.intellij.its.utils.OrchestratorUtils.executeBuildWithSonarScanner
import org.sonarlint.intellij.its.utils.OrchestratorUtils.generateTokenNameAndValue
import org.sonarlint.intellij.its.utils.OrchestratorUtils.newAdminWsClientWithUser
import org.sonarlint.intellij.its.utils.SettingsUtils.clearConnectionsAndAddSonarQubeConnection

const val ANSIBLE_PROJECT_KEY = "sample-ansible"
const val SHELL_PROJECT_KEY = "sample-shell"
const val AZURE_PIPELINES_PROJECT_KEY = "sample-azurepipelines"

@Tag("ConnectedAnalysisTests")
@EnabledIf("isIdeaCommunity")
class IaCTests : BaseUiTest() {

    @Test
    fun should_display_ansible_issue() = uiTest {
        verifyConnectedModeIssue(
            projectName = "sample-ansible",
            filePath = "HostNamespacesCheck/tasks/HostNamespacesCheck.yaml",
            projectKey = ANSIBLE_PROJECT_KEY,
            "Found 1 issue",
            "Use a specific version tag for the image."
        )
    }

    @Test
    fun should_display_shell_issue() = uiTest {
        verifyConnectedModeIssue(
            projectName = "sample-shell",
            filePath = "foo.sh",
            projectKey = SHELL_PROJECT_KEY,
            "Found 1 issue",
            "Prefix files and paths with \"./\" or \"--\" when using glob."
        )
    }

    @Test
    fun should_display_azure_pipelines_issue() = uiTest {
        verifyConnectedModeIssue(
            projectName = "sample-azurepipelines",
            filePath = "azure-pipelines.yml",
            projectKey = AZURE_PIPELINES_PROJECT_KEY,
            "Found 1 issue",
            "Complete the task associated to this \"TODO\" comment."
        )
    }

    private fun verifyConnectedModeIssue(projectName: String, filePath: String, projectKey: String, vararg expectedMessages: String) {
        openExistingProject(projectName)
        openFile(filePath)
        verifyCurrentFileTabContainsMessages("No findings to display")

        enableConnectedModeFromCurrentFilePanel(projectKey, true, "Orchestrator")

        verifyCurrentFileTabContainsMessages(*expectedMessages)
    }

    companion object {
        private val ORCHESTRATOR: OrchestratorExtension = defaultBuilderEnv()
            .setEdition(Edition.ENTERPRISE)
            .activateLicense()
            .keepBundledPlugins()
            .restoreProfileAtStartup(FileLocation.ofClasspath("/ansible-issue.xml"))
            .restoreProfileAtStartup(FileLocation.ofClasspath("/shell-issue.xml"))
            .restoreProfileAtStartup(FileLocation.ofClasspath("/azurepipelines-issue.xml"))
            .build()

        @JvmStatic
        @BeforeAll
        fun createSonarLintUser() {
            ORCHESTRATOR.start()

            val adminWsClient = newAdminWsClientWithUser(ORCHESTRATOR.server)
            val response = generateTokenNameAndValue(adminWsClient, "sonarlintUser")
            val token = response.second

            provisionAndAnalyzeProject(
                ANSIBLE_PROJECT_KEY,
                "Sample Ansible Issues",
                "ansible",
                "SonarLint IT Ansible Issue",
                "projects/sample-ansible/"
            )
            provisionAndAnalyzeProject(
                SHELL_PROJECT_KEY,
                "Sample Shell Issues",
                "shell",
                "SonarLint IT Shell Issue",
                "projects/sample-shell/"
            )
            provisionAndAnalyzeProject(
                AZURE_PIPELINES_PROJECT_KEY,
                "Sample Azure Pipelines Issues",
                "azurepipelines",
                "SonarLint IT Azure Pipelines Issue",
                "projects/sample-azurepipelines/"
            )

            clearConnectionsAndAddSonarQubeConnection(ORCHESTRATOR.server.url, token)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            ORCHESTRATOR.stop()
        }

        private fun provisionAndAnalyzeProject(projectKey: String, projectName: String, language: String, qualityProfile: String, projectPath: String) {
            ORCHESTRATOR.server.provisionProject(projectKey, projectName)
            ORCHESTRATOR.server.associateProjectToQualityProfile(projectKey, language, qualityProfile)
            executeBuildWithSonarScanner(projectPath, ORCHESTRATOR, projectKey)
        }
    }

}
