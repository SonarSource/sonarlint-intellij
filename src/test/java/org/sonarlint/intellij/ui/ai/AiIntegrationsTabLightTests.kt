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

import com.intellij.openapi.project.Project
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.sonarlint.intellij.AbstractSonarLintUILightTests
import org.sonarlint.intellij.ai.CLI_GUIDE_LABEL
import org.sonarlint.intellij.ai.CliCommand
import org.sonarlint.intellij.ai.CliTerminalAdapter
import org.sonarlint.intellij.ai.INSTALLED_STATUS
import org.sonarlint.intellij.ai.INSTALL_CLI_LABEL
import org.sonarlint.intellij.ai.MCP_GUIDE_LABEL
import org.sonarlint.intellij.ai.TerminalCompletion
import org.sonarlint.intellij.ai.TerminalLaunch
import org.sonarlint.intellij.fixtures.pumpEdtUntil
import org.sonarlint.intellij.ui.page.AiIntegrationCardPage

// The fake CLI is a shell script, while the backend only accepts sonar.exe on Windows
@DisabledOnOs(OS.WINDOWS)
class AiIntegrationsTabLightTests : AbstractSonarLintUILightTests() {

    private val cli = FakeSonarQubeCli()
    private val terminal = FakeTerminal()

    // The terminal is where the real install command would download the CLI from the internet
    override fun beforeMainToolWindowCreated() {
        cli.uninstall()
        replaceApplicationService(CliTerminalAdapter::class.java, terminal)
    }

    @AfterEach
    fun uninstallCli() {
        cli.uninstall()
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
        assumeNoRealCliDetected(cliCard)
        assertThat(cliCard.buttons).contains(INSTALL_CLI_LABEL)

        cliCard.clickButton(INSTALL_CLI_LABEL)
        awaitUntil { terminal.launchedCommand != null }
        assertThat(terminal.launchedCommand?.arguments.orEmpty().joinToString(" ")).contains("user-scripts/install.sh")

        cli.install()
        terminal.exit(0)

        awaitUntil { cliCard.showsFakeCliVersion() }
        assertThat(cliCard.tickedLabels).contains(INSTALLED_STATUS)
        assertThat(cliCard.labels).anyMatch { it.endsWith("v${FakeSonarQubeCli.VERSION}") }
        assertThat(cliCard.buttons).doesNotContain(INSTALL_CLI_LABEL)
    }

    @Test
    fun should_show_cli_as_installed_after_refreshing_when_installed_outside_the_ide() {
        val aiIntegrations = mainToolWindow.openAiIntegrations()
        val cliCard = aiIntegrations.sonarQubeCliCard
        awaitCliCheckFinished(cliCard)
        assumeNoRealCliDetected(cliCard)

        cli.install()
        aiIntegrations.refresh()

        awaitUntil { cliCard.showsFakeCliVersion() }
        assertThat(cliCard.tickedLabels).contains(INSTALLED_STATUS)
        assertThat(cliCard.labels).anyMatch { it.endsWith("v${FakeSonarQubeCli.VERSION}") }
    }

    @Test
    fun should_show_cli_as_installed_when_detected() {
        cli.install()

        val cliCard = mainToolWindow.openAiIntegrations().sonarQubeCliCard

        awaitUntil { cliCard.tickedLabels.contains(INSTALLED_STATUS) }
        assumeNoRealCliDetected(cliCard)
        assertThat(cliCard.tickedLabels).contains(INSTALLED_STATUS)
        assertThat(cliCard.labels).anyMatch { it.endsWith("v${FakeSonarQubeCli.VERSION}") }
    }

    /** Waits until the CLI card has finished checking, whatever the result. */
    private fun awaitCliCheckFinished(cliCard: AiIntegrationCardPage) {
        awaitUntil { cliCard.buttons.contains(INSTALL_CLI_LABEL) || cliCard.tickedLabels.contains(INSTALLED_STATUS) }
    }

    /**
     * On macOS the backend searches the system paths from /etc/paths instead of PATH, which the build cannot control.
     * A CLI installed there, e.g. in /usr/local/bin, is found before the fake one, so these scenarios cannot run on that machine.
     */
    private fun assumeNoRealCliDetected(cliCard: AiIntegrationCardPage) {
        assumeFalse(
            cliCard.tickedLabels.contains(INSTALLED_STATUS) && !cliCard.showsFakeCliVersion(),
            "A SonarQube CLI installed on this machine is visible to the backend: ${cliCard.labels}",
        )
    }

    private fun AiIntegrationCardPage.showsFakeCliVersion() = labels.any { it.endsWith("v${FakeSonarQubeCli.VERSION}") }

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

        fun exit(code: Int) {
            completion.complete(TerminalCompletion.Exited(code))
        }
    }
}
