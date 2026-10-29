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
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.sonarlint.intellij.AbstractSonarLintUILightTests
import org.sonarlint.intellij.ai.AiAgentRegistry
import org.sonarlint.intellij.ai.CHECKING_STATUS
import org.sonarlint.intellij.ai.CLI_GUIDE_LABEL
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
import org.sonarlint.intellij.fixtures.pumpEdtUntil
import org.sonarlint.intellij.ui.page.AiIntegrationCardPage
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent

// The fake CLIs are shell scripts, while the backend only accepts sonar.exe on Windows
@DisabledOnOs(OS.WINDOWS)
class AiIntegrationsTabLightTests : AbstractSonarLintUILightTests() {

    private val cli = FakeSonarQubeCli()
    private val terminal = FakeTerminal()
    private val notifications = mutableListOf<Notification>()
    private val claudeCode = AiAgentRegistry().displayName(AiAgent.CLAUDE_CODE)
    private val testDisposable = Disposer.newDisposable("AiIntegrationsTabLightTests")

    // The terminal is where the real commands would download the CLI or change the user's setup
    override fun beforeMainToolWindowCreated() {
        cli.uninstall()
        replaceApplicationService(CliTerminalAdapter::class.java, terminal)
        ApplicationManager.getApplication().messageBus.connect(testDisposable)
            .subscribe(Notifications.TOPIC, object : Notifications {
                override fun notify(notification: Notification) {
                    notifications.add(notification)
                }
            })
    }

    @AfterEach
    fun uninstallCli() {
        Disposer.dispose(testDisposable)
        cli.uninstall()
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
        val cliCard = startCliInstall()

        cli.install()
        terminal.finish(TerminalCompletion.Exited(0))

        awaitUntil { cliCard.showsFakeCli() }
        assertThat(cliCard.tickedLabels).contains(INSTALLED_STATUS)
        assertThat(cliCard.labels).anyMatch { it.endsWith("v${FakeSonarQubeCli.VERSION}") }
        assertThat(cliCard.buttons).doesNotContain(INSTALL_CLI_LABEL)
    }

    @Test
    fun should_keep_offering_install_and_report_the_error_when_install_fails() {
        val cliCard = startCliInstall()

        terminal.finish(TerminalCompletion.Exited(1))

        awaitUntil { notifications.isNotEmpty() }
        assertThat(notifications).anyMatch { it.content.contains("exited with code 1") }
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)
        assertThat(cliCard.tickedLabels).doesNotContain(INSTALLED_STATUS)
    }

    @Test
    fun should_keep_offering_install_when_install_is_cancelled() {
        val cliCard = startCliInstall()

        terminal.finish(TerminalCompletion.Cancelled)

        awaitUntil { notifications.isNotEmpty() }
        assertThat(notifications).anyMatch { it.content.contains("was cancelled") }
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)
        assertThat(cliCard.tickedLabels).doesNotContain(INSTALLED_STATUS)
    }

    @Test
    fun should_keep_offering_install_when_the_terminal_closes_without_exit_status() {
        val cliCard = startCliInstall()

        terminal.finish(TerminalCompletion.ClosedWithoutExitStatus)

        awaitUntil { notifications.isNotEmpty() }
        assertThat(notifications).anyMatch { it.content.contains("exit status is unavailable") }
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)
        assertThat(cliCard.tickedLabels).doesNotContain(INSTALLED_STATUS)
    }

    @Test
    fun should_show_cli_as_installed_after_refreshing_when_installed_outside_the_ide() {
        val aiIntegrations = mainToolWindow.openAiIntegrations()
        val cliCard = aiIntegrations.sonarQubeCliCard
        awaitCliCheckFinished(cliCard)
        assumeNoRealCliDetected(cliCard)

        cli.install()
        aiIntegrations.refresh()

        awaitUntil { cliCard.showsFakeCli() }
        assertThat(cliCard.tickedLabels).contains(INSTALLED_STATUS)
        assertThat(cliCard.labels).anyMatch { it.endsWith("v${FakeSonarQubeCli.VERSION}") }
    }

    @Test
    fun should_show_cli_as_installed_when_detected() {
        cli.install()

        val cliCard = openCliCard()

        assertThat(cliCard.tickedLabels).contains(INSTALLED_STATUS)
        assertThat(cliCard.labels).anyMatch { it.endsWith("v${FakeSonarQubeCli.VERSION}") }
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
    fun should_show_cli_as_signed_in_after_signing_in_from_the_card() {
        cli.install(authenticated = false)
        val cliCard = openCliCard()
        assertThat(cliCard.buttons).contains(SIGN_IN_LABEL)

        cliCard.clickButton(SIGN_IN_LABEL)
        awaitUntil { terminal.launchedCommand != null }
        assertThat(terminal.launchedCommand?.arguments.orEmpty().joinToString(" ")).contains("auth login")

        cli.signIn()
        terminal.finish(TerminalCompletion.Exited(0))

        awaitUntil { cliCard.labels.any { it.contains("Authenticated with ${FakeSonarQubeCli.ORGANIZATION}") } }
        assertThat(cliCard.labels).anyMatch { it.contains("Authenticated with ${FakeSonarQubeCli.ORGANIZATION}") }
        assertThat(cliCard.buttons).doesNotContain(SIGN_IN_LABEL)
    }

    @Test
    fun should_show_cli_as_not_installed_after_uninstalling_it_from_the_card() {
        cli.install(authenticated = true)
        TestDialogManager.setTestDialog(TestDialog.OK)
        val cliCard = openCliCard()
        assertThat(cliCard.hasUninstallAction).isTrue()

        cliCard.uninstall()

        awaitUntil { cliCard.buttons.contains(INSTALL_CLI_LABEL) }
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)
        assertThat(cli.receivedCommands).contains("system reset --force")
        assertThat(cli.isInstalled).isFalse()
    }

    @Test
    fun should_show_claude_code_integration_as_recorded_after_integrating_it_from_the_card() {
        cli.install(authenticated = true)
        val cliCard = openCliCard()
        cliCard.showAgents()
        awaitUntil { cliCard.hasAgent(claudeCode) }
        val claudeRow = cliCard.agent(claudeCode)
        assertThat(claudeRow.buttons).contains(INTEGRATE_LABEL)

        claudeRow.clickButton(INTEGRATE_LABEL)
        awaitUntil { terminal.launchedCommand != null }
        assertThat(terminal.launchedCommand?.arguments.orEmpty()).contains("integrate")

        cli.recordIntegration("claude-code")
        terminal.finish(TerminalCompletion.Exited(0))

        awaitUntil { cliCard.hasAgent(claudeCode) && cliCard.agent(claudeCode).tickedLabels.contains(INTEGRATION_RECORDED_STATUS) }
        assertThat(cliCard.agent(claudeCode).tickedLabels).contains(INTEGRATION_RECORDED_STATUS)
    }

    /** Opens the tab with no CLI installed and clicks "Install SonarQube CLI". */
    private fun startCliInstall(): AiIntegrationCardPage {
        val cliCard = mainToolWindow.openAiIntegrations().sonarQubeCliCard
        awaitCliCheckFinished(cliCard)
        assumeNoRealCliDetected(cliCard)
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)

        cliCard.clickButton(INSTALL_CLI_LABEL)
        awaitUntil { terminal.launchedCommand != null }
        assertThat(terminal.launchedCommand?.arguments.orEmpty().joinToString(" ")).contains("user-scripts/install.sh")
        return cliCard
    }

    /** Opens the tab with the fake CLI installed. */
    private fun openCliCard(): AiIntegrationCardPage {
        val cliCard = mainToolWindow.openAiIntegrations().sonarQubeCliCard
        awaitCliCheckFinished(cliCard)
        assumeNoRealCliDetected(cliCard)
        return cliCard
    }

    /** Waits until the CLI card has finished checking, whatever the result. */
    private fun awaitCliCheckFinished(cliCard: AiIntegrationCardPage) {
        awaitUntil { cliCard.labels.isNotEmpty() && !cliCard.labels.contains(CHECKING_STATUS) }
    }

    /**
     * On macOS the backend searches the system paths from /etc/paths instead of PATH, which the build cannot control.
     * A CLI installed there, e.g. in /usr/local/bin, is found before the fake one, so these scenarios cannot run on that machine.
     */
    private fun assumeNoRealCliDetected(cliCard: AiIntegrationCardPage) {
        assumeFalse(
            cliCard.tickedLabels.contains(INSTALLED_STATUS) && !cliCard.showsFakeCli(),
            "A SonarQube CLI installed on this machine is visible to the backend: ${cliCard.labels}",
        )
    }

    private fun AiIntegrationCardPage.showsFakeCli() = labels.any { it.endsWith("v${FakeSonarQubeCli.VERSION}") }

    /** The backend answers asynchronously; on timeout, let the following assertion report what is shown. */
    private fun awaitUntil(condition: () -> Boolean) {
        runCatching { pumpEdtUntil(timeoutMs = 30_000, condition = condition) }
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
        fun installClaudeCode() {
            assumeTrue(isSloopUserHomeIsolated(), "The build could not isolate the backend's user home, see build.gradle.kts")
            FakeClaudeCode.install()
        }
    }
}
