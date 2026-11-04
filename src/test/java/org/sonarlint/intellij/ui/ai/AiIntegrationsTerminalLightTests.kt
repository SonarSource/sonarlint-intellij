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
import org.sonarlint.intellij.ai.CLI_TERMINAL_TAB_NAME
import org.sonarlint.intellij.ai.INTEGRATE_LABEL
import org.sonarlint.intellij.ai.INTEGRATION_RECORDED_STATUS
import org.sonarlint.intellij.ai.SIGN_IN_LABEL
import org.sonarlint.intellij.fixtures.awaitUntil
import org.sonarlint.intellij.fixtures.pumpEdt
import org.sonarlint.intellij.ui.page.AiIntegrationCardPage
import org.sonarlint.intellij.ui.page.TerminalToolWindowPage
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAgent

/**
 * The commands that need the user run in the real IDE terminal, which runs the fake SonarQube CLI. Installing stays in
 * [AiIntegrationsTabLightTests] with a fake terminal: the install command downloads the real CLI.
 */
// The fake CLIs are shell scripts, while the backend only accepts sonar.exe on Windows
@DisabledOnOs(OS.WINDOWS)
class AiIntegrationsTerminalLightTests : AbstractSonarLintUILightTests() {

    private val cli = FakeSonarQubeCli()
    private lateinit var terminal: TerminalToolWindowPage
    private lateinit var notifications: List<Notification>
    private val claudeCode = AiAgentRegistry().displayName(AiAgent.CLAUDE_CODE)

    override fun beforeMainToolWindowCreated() {
        cli.uninstall()
        terminal = TerminalToolWindowPage.create(project, testRootDisposable)
        notifications = recordNotifications()
    }

    @AfterEach
    fun closeTerminalAndUninstallCli() {
        terminal.closeAllTabs()
        cli.uninstall()
    }

    @Test
    fun should_show_cli_as_signed_in_after_signing_in_in_the_terminal() {
        cli.install(authenticated = false)
        val cliCard = openCliCard()
        assertThat(cliCard.buttons).contains(SIGN_IN_LABEL)

        cliCard.clickButton(SIGN_IN_LABEL)

        awaitUntil { cliCard.labels.any { it.contains("Authenticated with ${FakeSonarQubeCli.ORGANIZATION}") } }
        assertThat(cliCard.labels).anyMatch { it.contains("Authenticated with ${FakeSonarQubeCli.ORGANIZATION}") }
        assertThat(cliCard.buttons).doesNotContain(SIGN_IN_LABEL)
        assertThat(terminal.openedTabNames).contains(CLI_TERMINAL_TAB_NAME)
        assertThat(cli.receivedCommands).anyMatch { it.startsWith("auth login") }
    }

    @Test
    fun should_keep_offering_sign_in_and_report_the_exit_code_when_sign_in_fails_in_the_terminal() {
        cli.install(authenticated = false)
        cli.failNextCommand(exitCode = 3)
        val cliCard = openCliCard()

        cliCard.clickButton(SIGN_IN_LABEL)

        awaitUntil { notifications.isNotEmpty() }
        awaitCardReloaded(cliCard)
        assertThat(notifications).anyMatch { it.content.contains("exited with code 3") }
        assertThat(cliCard.buttons).contains(SIGN_IN_LABEL)
        assertThat(cli.receivedCommands).anyMatch { it.startsWith("auth login") }
    }

    @Test
    fun should_show_claude_code_integration_as_recorded_after_integrating_it_in_the_terminal() {
        cli.install(authenticated = true)
        val cliCard = openCliCard()
        cliCard.showAgents()
        awaitUntil { cliCard.hasAgent(claudeCode) }
        val claudeRow = cliCard.agent(claudeCode)
        assertThat(claudeRow.buttons).contains(INTEGRATE_LABEL)

        claudeRow.clickButton(INTEGRATE_LABEL)

        awaitUntil { cliCard.hasAgent(claudeCode) && cliCard.agent(claudeCode).tickedLabels.contains(INTEGRATION_RECORDED_STATUS) }
        assertThat(cliCard.agent(claudeCode).tickedLabels).contains(INTEGRATION_RECORDED_STATUS)
        assertThat(terminal.openedTabNames).contains(CLI_TERMINAL_TAB_NAME)
        assertThat(cli.receivedCommands).contains("integrate claude --global")
    }

    /** Opens the tab with the fake CLI installed. */
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

    private fun awaitCliCheckFinished(cliCard: AiIntegrationCardPage) {
        awaitUntil { cliCard.labels.isNotEmpty() && !cliCard.labels.contains(CHECKING_STATUS) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun requireIsolatedBackend() {
            assumeTrue(isSloopUserHomeIsolated(), "The build could not isolate the backend's user home, see build.gradle.kts")
        }
    }
}
