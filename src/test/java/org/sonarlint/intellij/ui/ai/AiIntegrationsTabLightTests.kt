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
package org.sonarlint.intellij.ui.ai

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import java.util.Base64
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.RegisterExtension
import org.sonarlint.intellij.AbstractSonarLintUILightTests
import org.sonarlint.intellij.ai.AUTHENTICATED_STATUS
import org.sonarlint.intellij.ai.AiAgentRegistry
import org.sonarlint.intellij.ai.CHECKING_STATUS
import org.sonarlint.intellij.ai.CLI_GUIDE_LABEL
import org.sonarlint.intellij.ai.CLI_TERMINAL_TAB_NAME
import org.sonarlint.intellij.ai.CliCommand
import org.sonarlint.intellij.ai.CliTerminalAdapter
import org.sonarlint.intellij.ai.INSTALLED_STATUS
import org.sonarlint.intellij.ai.INSTALL_CLI_LABEL
import org.sonarlint.intellij.ai.INTEGRATE_LABEL
import org.sonarlint.intellij.ai.INTEGRATION_RECORDED_STATUS
import org.sonarlint.intellij.ai.MCP_GUIDE_LABEL
import org.sonarlint.intellij.ai.SIGN_IN_LABEL
import org.sonarlint.intellij.ai.TROUBLESHOOT_LABEL
import org.sonarlint.intellij.ai.TerminalCompletion
import org.sonarlint.intellij.ai.TerminalLaunch
import org.sonarlint.intellij.ai.UNAVAILABLE_STATUS
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.fixtures.MockWebServerExtension
import org.sonarlint.intellij.fixtures.awaitUntil
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.ui.page.AiIntegrationCardPage
import org.sonarlint.intellij.ui.page.TerminalToolWindowPage
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto

// Not yet on Windows: the fake Claude Code is a shell script, and SonarQubeCli only installs the CLI at its Unix location
@DisabledOnOs(OS.WINDOWS)
class AiIntegrationsTabLightTests : AbstractSonarLintUILightTests() {

    private val cli = SonarQubeCli()
    private val sonarQubeServerConnection = ServerConnection.newBuilder().setName("Fake SonarQube Server").setHostUrl(sonarQubeServer.url("")).build()
    private val recordingTerminal = RecordingTerminal()
    private lateinit var terminal: TerminalToolWindowPage
    private val claudeCode = AiAgentRegistry().displayName(AiAgent.CLAUDE_CODE)
    private lateinit var notifications: List<Notification>

    override fun beforeMainToolWindowCreated() {
        cli.reset()
        answerSonarQubeCliRequests(tokenValid = true)
        terminal = TerminalToolWindowPage.create(project, testRootDisposable)
        notifications = recordNotifications()
    }

    @AfterEach
    fun closeTerminalAndUninstallCli() {
        terminal.closeAllTabs()
        cli.reset()
    }

    @Test
    fun should_show_sonarqube_cli_and_mcp_server_cards_with_their_guides() {
        val aiIntegrations = mainToolWindow.openAiIntegrations()

        assertThat(aiIntegrations.hasSonarQubeCliCard).isTrue()
        assertThat(aiIntegrations.hasSonarQubeMcpServerCard).isTrue()
        assertThat(aiIntegrations.sonarQubeCliCard.documentationLinks).contains(CLI_GUIDE_LABEL)
        assertThat(aiIntegrations.sonarQubeMcpServerCard.documentationLinks).contains(MCP_GUIDE_LABEL)
    }

    @Test
    fun should_show_cli_as_installed_after_installing_it_from_the_card() {
        val cliCard = startCliInstall()

        // What the install script does, without downloading the CLI
        cli.install()
        recordingTerminal.finish(TerminalCompletion.Exited(0))

        awaitUntil { cliCard.tickedLabels.contains(INSTALLED_STATUS) }
        assertThat(cliCard.tickedLabels).contains(INSTALLED_STATUS)
        assertThat(cliCard.labels).anyMatch { it.endsWith("v${cli.version}") }
        assertThat(cliCard.buttons).doesNotContain(INSTALL_CLI_LABEL)
    }

    @Test
    fun should_keep_offering_install_and_report_the_error_when_install_fails() {
        val cliCard = startCliInstall()

        recordingTerminal.finish(TerminalCompletion.Exited(1))

        awaitUntil { notifications.isNotEmpty() }
        awaitCardReloaded(cliCard)
        assertThat(notifications).anyMatch { it.content.contains("exited with code 1") }
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)
        assertThat(cliCard.tickedLabels).doesNotContain(INSTALLED_STATUS)
    }

    @Test
    fun should_keep_offering_install_when_install_is_cancelled() {
        val cliCard = startCliInstall()

        recordingTerminal.finish(TerminalCompletion.Cancelled)

        awaitUntil { notifications.isNotEmpty() }
        awaitCardReloaded(cliCard)
        assertThat(notifications).anyMatch { it.content.contains("was cancelled") }
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)
        assertThat(cliCard.tickedLabels).doesNotContain(INSTALLED_STATUS)
    }

    @Test
    fun should_keep_offering_install_when_the_terminal_closes_without_exit_status() {
        val cliCard = startCliInstall()

        recordingTerminal.finish(TerminalCompletion.ClosedWithoutExitStatus)

        awaitUntil { notifications.isNotEmpty() }
        awaitCardReloaded(cliCard)
        assertThat(notifications).anyMatch { it.content.contains("exit status is unavailable") }
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)
        assertThat(cliCard.tickedLabels).doesNotContain(INSTALLED_STATUS)
    }

    @Test
    fun should_show_cli_as_installed_after_refreshing_when_installed_outside_the_ide() {
        val aiIntegrations = mainToolWindow.openAiIntegrations()
        val cliCard = aiIntegrations.sonarQubeCliCard
        awaitCliCheckFinished(cliCard)

        cli.install()
        aiIntegrations.refresh()

        awaitUntil { cliCard.tickedLabels.contains(INSTALLED_STATUS) }
        assertThat(cliCard.tickedLabels).contains(INSTALLED_STATUS)
        assertThat(cliCard.labels).anyMatch { it.endsWith("v${cli.version}") }
    }

    @Test
    fun should_show_cli_as_installed_when_detected() {
        cli.install()

        val cliCard = openCliCard()

        assertThat(cliCard.tickedLabels).contains(INSTALLED_STATUS)
        assertThat(cliCard.labels).anyMatch { it.endsWith("v${cli.version}") }
    }

    @Test
    fun should_offer_troubleshooting_when_the_cli_does_not_work() {
        cli.install()
        cli.breakInstallation()

        val cliCard = openCliCard()

        assertThat(cliCard.labels).contains(UNAVAILABLE_STATUS)
        assertThat(cliCard.buttons).contains(TROUBLESHOOT_LABEL)
        assertThat(cliCard.tickedLabels).doesNotContain(INSTALLED_STATUS)
    }

    @Test
    fun should_show_cli_as_not_installed_after_uninstalling_it_from_the_card() {
        cli.install()
        TestDialogManager.setTestDialog(TestDialog.OK)
        val cliCard = openCliCard()
        assertThat(cliCard.hasUninstallAction).isTrue()

        cliCard.uninstall()

        awaitUntil { cliCard.buttons.contains(INSTALL_CLI_LABEL) }
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)
        assertThat(cli.isInstalled).isFalse()
    }

    @Test
    fun should_show_cli_as_signed_in_after_signing_in_with_the_ide_connection() {
        cli.install()
        connectToSonarQubeServer(TOKEN)
        // The backend signs the CLI in with the connection's token, without a terminal; a terminal would mean a browser login
        replaceApplicationService(CliTerminalAdapter::class.java, recordingTerminal)
        val cliCard = openCliCard()

        cliCard.clickButton(SIGN_IN_LABEL)

        awaitUntil { cliCard.labels.any { it.startsWith(AUTHENTICATED_STATUS) } }
        assertThat(cliCard.labels).describedAs("unanswered: ${sonarQubeServer.unansweredRequests}").anyMatch { it.startsWith(AUTHENTICATED_STATUS) }
        assertThat(cliCard.buttons).doesNotContain(SIGN_IN_LABEL)
        assertThat(notifications).anyMatch { it.content.contains("SonarQube CLI authenticated") }
        assertThat(recordingTerminal.launchedCommand).isNull()
        assertThat(tokensSentToValidate()).contains(TOKEN)
    }

    @Test
    fun should_keep_offering_sign_in_and_report_the_error_when_the_connection_token_is_rejected() {
        cli.install()
        answerSonarQubeCliRequests(tokenValid = false)
        connectToSonarQubeServer(REJECTED_TOKEN)
        replaceApplicationService(CliTerminalAdapter::class.java, recordingTerminal)
        val cliCard = openCliCard()

        cliCard.clickButton(SIGN_IN_LABEL)

        awaitUntil { notifications.isNotEmpty() }
        awaitCardReloaded(cliCard)
        assertThat(notifications).anyMatch { it.type == NotificationType.ERROR && it.content.contains("SonarQube CLI authentication failed") }
        assertThat(cliCard.buttons).contains(SIGN_IN_LABEL)
        assertThat(recordingTerminal.launchedCommand).isNull()
        assertThat(tokensSentToValidate()).contains(REJECTED_TOKEN)
    }

    @Test
    fun should_sign_in_in_the_terminal_when_there_is_no_ide_connection() {
        cli.install()
        // The real command opens a browser
        replaceApplicationService(CliTerminalAdapter::class.java, recordingTerminal)
        val cliCard = openCliCard()

        cliCard.clickButton(SIGN_IN_LABEL)

        awaitUntil { recordingTerminal.launchedCommand != null }
        val arguments = recordingTerminal.launchedCommand?.arguments.orEmpty()
        assertThat(arguments.take(2)).containsExactly("auth", "login")
        assertThat(arguments).doesNotContain("--with-token")
        recordingTerminal.finish(TerminalCompletion.Cancelled)
    }

    @Test
    fun should_show_claude_code_integration_as_recorded_after_integrating_it_in_the_terminal() {
        cli.install()
        cli.signIn(sonarQubeServer.url(""), TOKEN)
        val cliCard = openCliCard()
        cliCard.showAgents()
        awaitUntil { cliCard.hasAgent(claudeCode) }
        val claudeRow = cliCard.agent(claudeCode)
        assertThat(claudeRow.buttons).contains(INTEGRATE_LABEL)

        claudeRow.clickButton(INTEGRATE_LABEL)
        // `sonar integrate` asks what to install; the recommended set is the default
        awaitUntil { terminal.lastTerminalText.contains("Install recommended") }
        terminal.typeInLastTerminal("\r")

        awaitUntil { cliCard.hasAgent(claudeCode) && cliCard.agent(claudeCode).tickedLabels.contains(INTEGRATION_RECORDED_STATUS) }
        assertThat(cliCard.agent(claudeCode).tickedLabels).contains(INTEGRATION_RECORDED_STATUS)
        assertThat(terminal.openedTabNames).contains(CLI_TERMINAL_TAB_NAME)
    }

    /**
     * Opens the tab with no CLI installed and clicks "Install SonarQube CLI" in a terminal that only records the command:
     * the test then decides how it ends, e.g. failed or cancelled.
     */
    private fun startCliInstall(): AiIntegrationCardPage {
        replaceApplicationService(CliTerminalAdapter::class.java, recordingTerminal)
        val cliCard = mainToolWindow.openAiIntegrations().sonarQubeCliCard
        awaitCliCheckFinished(cliCard)
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)

        cliCard.clickButton(INSTALL_CLI_LABEL)
        awaitUntil { recordingTerminal.launchedCommand != null }
        assertThat(recordingTerminal.launchedCommand?.arguments.orEmpty().joinToString(" ")).contains("user-scripts/install.sh")
        return cliCard
    }

    /**
     * What SonarQube Server answers to the requests the SonarQube CLI makes to sign in with a token and to integrate an
     * agent, without entitlement to the agentic features.
     */
    private fun answerSonarQubeCliRequests(tokenValid: Boolean) {
        sonarQubeServer.addStringResponse("/api/authentication/validate", """{"valid":$tokenValid}""")
        sonarQubeServer.addStringResponse("/api/users/current", """{"isLoggedIn":true,"login":"user"}""")
        sonarQubeServer.addStringResponse("/api/system/status", """{"id":"20160308094653","version":"2025.1","status":"UP"}""")
        sonarQubeServer.addStringResponse("/api/v2/a3s/org-entitlement/$NO_ORGANIZATION", "{}")
        sonarQubeServer.addStringResponse("/api/v2/cag/cag-entitlement/$NO_ORGANIZATION", "{}")
    }

    /** The tokens the fake server was asked to validate, from bearer or basic authorization. */
    private fun tokensSentToValidate() = sonarQubeServer.receivedRequests
        .filter { it.path == "/api/authentication/validate" }
        .mapNotNull { request ->
            val authorization = request.authorization ?: return@mapNotNull null
            when {
                authorization.startsWith("Bearer ") -> authorization.removePrefix("Bearer ")
                authorization.startsWith("Basic ") -> String(Base64.getDecoder().decode(authorization.removePrefix("Basic "))).substringBefore(':')
                else -> null
            }
        }

    /** Creates the connection to the fake server as the settings do, with [token] saved in the credential store. */
    private fun connectToSonarQubeServer(token: String) {
        getService(CredentialsService::class.java).saveCredentials(sonarQubeServerConnection.name, Either.forLeft(TokenDto(token)))
        getGlobalSettings().addServerConnection(sonarQubeServerConnection)
        ApplicationManager.getApplication().messageBus.syncPublisher(GlobalConfigurationListener.TOPIC)
            .changed(getGlobalSettings().serverConnections)
    }

    /** Opens the tab with a CLI already installed. */
    private fun openCliCard(): AiIntegrationCardPage {
        val cliCard = mainToolWindow.openAiIntegrations().sonarQubeCliCard
        awaitCliCheckFinished(cliCard)
        return cliCard
    }

    /** The card reloads after a CLI command finishes: let that reload start, then wait for its result. */
    private fun awaitCardReloaded(cliCard: AiIntegrationCardPage) {
        pumpEdt()
        awaitCliCheckFinished(cliCard)
    }

    /** Waits until the CLI card has finished checking, whatever the result. */
    private fun awaitCliCheckFinished(cliCard: AiIntegrationCardPage) {
        awaitUntil { cliCard.labels.isNotEmpty() && !cliCard.labels.contains(CHECKING_STATUS) }
    }

    /** Records the command the card asks to run instead of opening a real terminal. */
    private class RecordingTerminal : CliTerminalAdapter {
        private val completion = CompletableFuture<TerminalCompletion>()
        var launchedCommand: CliCommand? = null
            private set

        override fun launch(project: Project, command: CliCommand): TerminalLaunch {
            launchedCommand = command
            return TerminalLaunch.Started({}, completion)
        }

        fun finish(result: TerminalCompletion) {
            completion.complete(result)
        }
    }

    companion object {
        private const val TOKEN = "token"
        private const val REJECTED_TOKEN = "rejected-token"
        /** The organization the CLI asks entitlements for on SonarQube Server, which has none */
        private const val NO_ORGANIZATION = "00000000-0000-0000-0000-000000000000"

        @JvmField
        @RegisterExtension
        val sonarQubeServer = MockWebServerExtension.onRandomPort()

        @JvmStatic
        @BeforeAll
        fun requireIsolatedBackendAndCli() {
            // The build also provides the fake Claude Code CLI there, see build.gradle.kts
            assumeTrue(isSloopUserHomeIsolated(), "The build could not isolate the backend's user home, see build.gradle.kts")
            assumeTrue(isSonarQubeCliAvailable(), "The build could not provide a SonarQube CLI for this machine, see build.gradle.kts")
        }
    }
}
