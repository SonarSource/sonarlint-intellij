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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.sonarlint.intellij.AbstractSonarLintUILightTests
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
import org.sonarlint.intellij.fixtures.awaitUntil
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.ui.page.AiIntegrationCardPage
import org.sonarlint.intellij.ui.page.TerminalToolWindowPage
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent

// The CLI wrapper and the fake Claude Code are shell scripts, while the backend only accepts sonar.exe on Windows
@DisabledOnOs(OS.WINDOWS)
class AiIntegrationsTabLightTests : AbstractSonarLintUILightTests() {

    private val realCli = RealSonarQubeCli()
    private val wrappedCli = WrappedSonarQubeCli(realCli)
    private val fakeTerminal = FakeTerminal()
    private lateinit var terminal: TerminalToolWindowPage
    private val claudeCode = AiAgentRegistry().displayName(AiAgent.CLAUDE_CODE)
    private lateinit var notifications: List<Notification>

    override fun beforeMainToolWindowCreated() {
        wrappedCli.uninstall()
        realCli.reset()
        terminal = TerminalToolWindowPage.create(project, testRootDisposable)
        notifications = recordNotifications()
    }

    @AfterEach
    fun closeTerminalAndUninstallCli() {
        terminal.closeAllTabs()
        realCli.reset()
        wrappedCli.uninstall()
        TestDialogManager.setTestDialog(TestDialog.DEFAULT)
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
        val cliCard = mainToolWindow.openAiIntegrations().sonarQubeCliCard
        awaitCliCheckFinished(cliCard)

        // The terminal runs the official install script, which downloads the latest release
        cliCard.clickButton(INSTALL_CLI_LABEL)

        awaitUntil(timeoutMs = 180_000) { cliCard.tickedLabels.contains(INSTALLED_STATUS) }
        assertThat(cliCard.tickedLabels).contains(INSTALLED_STATUS)
        assertThat(realCli.isInstalled).isTrue()
        assertThat(cliCard.labels).anyMatch { it.endsWith("v${realCli.version}") }
        assertThat(cliCard.buttons).doesNotContain(INSTALL_CLI_LABEL)
        assertThat(terminal.openedTabNames).contains(CLI_TERMINAL_TAB_NAME)
    }

    @Test
    fun should_keep_offering_install_and_report_the_error_when_install_fails() {
        val cliCard = startCliInstall()

        fakeTerminal.finish(TerminalCompletion.Exited(1))

        awaitUntil { notifications.isNotEmpty() }
        awaitCardReloaded(cliCard)
        assertThat(notifications).anyMatch { it.content.contains("exited with code 1") }
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)
        assertThat(cliCard.tickedLabels).doesNotContain(INSTALLED_STATUS)
    }

    @Test
    fun should_keep_offering_install_when_install_is_cancelled() {
        val cliCard = startCliInstall()

        fakeTerminal.finish(TerminalCompletion.Cancelled)

        awaitUntil { notifications.isNotEmpty() }
        awaitCardReloaded(cliCard)
        assertThat(notifications).anyMatch { it.content.contains("was cancelled") }
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)
        assertThat(cliCard.tickedLabels).doesNotContain(INSTALLED_STATUS)
    }

    @Test
    fun should_keep_offering_install_when_the_terminal_closes_without_exit_status() {
        val cliCard = startCliInstall()

        fakeTerminal.finish(TerminalCompletion.ClosedWithoutExitStatus)

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

        realCli.install()
        aiIntegrations.refresh()

        awaitUntil { cliCard.tickedLabels.contains(INSTALLED_STATUS) }
        assertThat(cliCard.tickedLabels).contains(INSTALLED_STATUS)
        assertThat(cliCard.labels).anyMatch { it.endsWith("v${realCli.version}") }
    }

    @Test
    fun should_show_cli_as_installed_when_detected() {
        realCli.install()

        val cliCard = openCliCard()

        assertThat(cliCard.tickedLabels).contains(INSTALLED_STATUS)
        assertThat(cliCard.labels).anyMatch { it.endsWith("v${realCli.version}") }
    }

    @Test
    fun should_offer_troubleshooting_when_the_cli_does_not_work() {
        wrappedCli.install()
        wrappedCli.breakInstallation()

        val cliCard = openCliCard()

        assertThat(cliCard.labels).contains(UNAVAILABLE_STATUS)
        assertThat(cliCard.buttons).contains(TROUBLESHOOT_LABEL)
        assertThat(cliCard.tickedLabels).doesNotContain(INSTALLED_STATUS)
    }

    @Test
    fun should_show_cli_as_not_installed_after_uninstalling_it_from_the_card() {
        realCli.install()
        TestDialogManager.setTestDialog(TestDialog.OK)
        val cliCard = openCliCard()
        assertThat(cliCard.hasUninstallAction).isTrue()

        cliCard.uninstall()

        awaitUntil { cliCard.buttons.contains(INSTALL_CLI_LABEL) }
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)
        assertThat(realCli.isInstalled).isFalse()
    }

    @Test
    fun should_show_cli_as_signed_in_after_signing_in_in_the_terminal() {
        assumeTrue(RealSonarQubeCli.canSignIn, "SONARCLOUD_IT_TOKEN is not set")
        // Signs in to SonarQube Cloud staging with a token instead of a browser
        wrappedCli.install()
        val cliCard = openCliCard()
        assertThat(cliCard.buttons).contains(SIGN_IN_LABEL)

        cliCard.clickButton(SIGN_IN_LABEL)

        awaitUntil(timeoutMs = 60_000) { cliCard.labels.any { it.contains("Authenticated with ${RealSonarQubeCli.ORGANIZATION}") } }
        assertThat(cliCard.labels).anyMatch { it.contains("Authenticated with ${RealSonarQubeCli.ORGANIZATION}") }
        assertThat(cliCard.buttons).doesNotContain(SIGN_IN_LABEL)
        assertThat(terminal.openedTabNames).contains(CLI_TERMINAL_TAB_NAME)
        assertThat(wrappedCli.receivedCommands).anyMatch { it.startsWith("auth login") }
    }

    @Test
    fun should_keep_offering_sign_in_and_report_the_exit_code_when_sign_in_fails_in_the_terminal() {
        wrappedCli.install()
        wrappedCli.failNextSignIn(exitCode = 3)
        val cliCard = openCliCard()

        cliCard.clickButton(SIGN_IN_LABEL)

        awaitUntil { notifications.isNotEmpty() }
        awaitCardReloaded(cliCard)
        assertThat(notifications).anyMatch { it.content.contains("exited with code 3") }
        assertThat(cliCard.buttons).contains(SIGN_IN_LABEL)
        assertThat(wrappedCli.receivedCommands).anyMatch { it.startsWith("auth login") }
    }

    @Test
    fun should_show_claude_code_integration_as_recorded_after_integrating_it_in_the_terminal() {
        assumeTrue(RealSonarQubeCli.canSignIn, "SONARCLOUD_IT_TOKEN is not set")
        realCli.install()
        realCli.signIn()
        val cliCard = openCliCard()
        cliCard.showAgents()
        awaitUntil { cliCard.hasAgent(claudeCode) }
        val claudeRow = cliCard.agent(claudeCode)
        assertThat(claudeRow.buttons).contains(INTEGRATE_LABEL)

        claudeRow.clickButton(INTEGRATE_LABEL)
        // `sonar integrate` asks what to install; the recommended set is the default
        awaitUntil(timeoutMs = 60_000) { terminal.lastTerminalText.contains("Install recommended") }
        terminal.typeInLastTerminal("\r")

        awaitUntil(timeoutMs = 120_000) {
            cliCard.hasAgent(claudeCode) && cliCard.agent(claudeCode).tickedLabels.contains(INTEGRATION_RECORDED_STATUS)
        }
        assertThat(cliCard.agent(claudeCode).tickedLabels).contains(INTEGRATION_RECORDED_STATUS)
        assertThat(terminal.openedTabNames).contains(CLI_TERMINAL_TAB_NAME)
    }

    /**
     * Opens the tab with no CLI installed and clicks "Install SonarQube CLI" in a fake terminal, which only records the
     * command: the test then decides how it ends, e.g. failed or cancelled.
     */
    private fun startCliInstall(): AiIntegrationCardPage {
        replaceApplicationService(CliTerminalAdapter::class.java, fakeTerminal)
        val cliCard = mainToolWindow.openAiIntegrations().sonarQubeCliCard
        awaitCliCheckFinished(cliCard)
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)

        cliCard.clickButton(INSTALL_CLI_LABEL)
        awaitUntil { fakeTerminal.launchedCommand != null }
        assertThat(fakeTerminal.launchedCommand?.arguments.orEmpty().joinToString(" ")).contains("user-scripts/install.sh")
        return cliCard
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
    private class FakeTerminal : CliTerminalAdapter {
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
        @JvmStatic
        @BeforeAll
        fun requireIsolatedBackend() {
            // The build also provides the fake Claude Code CLI there, see build.gradle.kts
            assumeTrue(isSloopUserHomeIsolated(), "The build could not isolate the backend's user home, see build.gradle.kts")
        }
    }
}
