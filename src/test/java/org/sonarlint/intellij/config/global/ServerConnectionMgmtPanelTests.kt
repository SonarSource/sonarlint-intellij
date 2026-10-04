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
package org.sonarlint.intellij.config.global

import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.credentials.ControllablePasswordSafe
import org.sonarlint.intellij.config.global.credentials.CredentialCancellation
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.config.global.credentials.awaitRelease
import org.sonarlint.intellij.config.global.credentials.awaitUi
import org.sonarlint.intellij.config.global.credentials.connection
import org.sonarlint.intellij.config.global.credentials.token

class ServerConnectionMgmtPanelTests : AbstractSonarLintLightTests() {
    @Test
    fun `removal targets captured connection after selection changes and rejects duplicate actions`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val safe = ControllablePasswordSafe().apply { onWrite = { _, _ -> entered.countDown(); awaitRelease(release) } }
        replaceApplicationService(CredentialsService::class.java, CredentialsService { safe })
        val first = connection("first")
        val second = connection("second")
        val panel = createPanel(first, second)
        remove(panel)
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        list(panel).setSelectedValue(second, true)
        remove(panel)
        assertThat(panel.connections).containsExactly(first, second)
        release.countDown()
        awaitUi { panel.connections.size == 1 }
        assertThat(panel.connections).containsExactly(second)
        assertThat(safe.writes).hasSize(2)
    }

    @Test
    fun `reset during removal retains replacement model and skips subsequent deletion`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val safe = ControllablePasswordSafe().apply { onWrite = { _, _ -> entered.countDown(); awaitRelease(release) } }
        val service = CredentialsService { safe }
        replaceApplicationService(CredentialsService::class.java, service)
        val original = connection()
        val panel = createPanel(original)
        remove(panel)
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        val replacement = connection()
        panel.load(SonarLintGlobalSettings().apply { serverConnections = listOf(replacement) })
        release.countDown()
        service.saveCredentials("connection", token("replacement-value"), CredentialCancellation()).get(5, TimeUnit.SECONDS)
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
        assertThat(panel.connections.single()).isSameAs(replacement)
        assertThat(panel.hasUnappliedConnectionChanges()).isFalse()
        assertThat(safe.writes).hasSize(2) // Entered erase, then replacement; no second erase.
    }

    @Test
    fun `dispose during edit retrieval prevents late wizard`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val safe = ControllablePasswordSafe().apply {
            seedToken("connection", "test-value")
            onRead = { entered.countDown(); awaitRelease(release) }
        }
        val service = CredentialsService { safe }
        replaceApplicationService(CredentialsService::class.java, service)
        val panel = createPanel(connection())
        val edit = ServerConnectionMgmtPanel::class.java.getDeclaredMethod("editSelectedConnection").apply { isAccessible = true }
        edit.invoke(panel)
        edit.invoke(panel)
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        Disposer.dispose(panel)
        release.countDown()
        service.getCredentials(connection(), CredentialCancellation()).get(5, TimeUnit.SECONDS)
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
        val wizard = ServerConnectionMgmtPanel::class.java.getDeclaredField("activeWizard").apply { isAccessible = true }
        assertThat(wizard.get(panel)).isNull()
    }

    private fun createPanel(vararg connections: ServerConnection): ServerConnectionMgmtPanel {
        val panel = ServerConnectionMgmtPanel()
        Disposer.register(testRootDisposable, panel)
        panel.component
        panel.load(SonarLintGlobalSettings().apply { serverConnections = connections.toList() })
        return panel
    }

    @Suppress("UNCHECKED_CAST")
    private fun list(panel: ServerConnectionMgmtPanel): JBList<ServerConnection> =
        ServerConnectionMgmtPanel::class.java.getDeclaredField("connectionList").apply { isAccessible = true }.get(panel) as JBList<ServerConnection>

    private fun remove(panel: ServerConnectionMgmtPanel) {
        val type = ServerConnectionMgmtPanel::class.java.declaredClasses.single { it.simpleName == "RemoveServerAction" }
        val action = type.declaredConstructors.single().apply { isAccessible = true }.newInstance(panel)
        type.getDeclaredMethod("run", com.intellij.ui.AnActionButton::class.java).apply { isAccessible = true }.invoke(action, null)
    }
}
