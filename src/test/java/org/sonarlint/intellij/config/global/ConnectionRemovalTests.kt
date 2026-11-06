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

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.AnActionButtonRunnable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.credentials.eraseToken
import org.sonarlint.intellij.config.global.credentials.getToken
import org.sonarlint.intellij.config.global.credentials.setToken

class ConnectionRemovalTests : AbstractSonarLintLightTests() {
    private val connection = ServerConnection.newBuilder().setName("removal-test").setHostUrl("https://example.org").build()

    @AfterEach
    fun clearCredentials() {
        PasswordSafe.instance.eraseToken(connection.name)
    }

    @Test
    fun `cancel preserves connection credentials`() {
        val configurable = createConfigurable()
        removeConnection(configurable)

        configurable.disposeUIResources()

        assertThat(globalSettings.serverConnections).containsExactly(connection)
        assertThat(PasswordSafe.instance.getToken(connection.name)).isEqualTo("test-token")
    }

    @Test
    fun `reset preserves connection credentials`() {
        val configurable = createConfigurable()
        try {
            removeConnection(configurable)

            configurable.reset()

            assertThat(configurable.currentConnections).containsExactly(connection)
            assertThat(PasswordSafe.instance.getToken(connection.name)).isEqualTo("test-token")
        } finally {
            configurable.disposeUIResources()
        }
    }

    @Test
    fun `apply deletes connection credentials`() {
        val configurable = createConfigurable()
        try {
            removeConnection(configurable)

            configurable.apply()

            assertThat(globalSettings.serverConnections).isEmpty()
            assertThat(PasswordSafe.instance.getToken(connection.name)).isNull()
        } finally {
            configurable.disposeUIResources()
        }
    }

    @Test
    fun `same name replacement keeps credentials`() {
        globalSettings.setServerConnections(listOf(connection))
        PasswordSafe.instance.setToken(connection.name, "test-token")
        val replacement = ServerConnection.newBuilder().setName(connection.name).setHostUrl("https://replacement.example.org").build()
        val proposed = SonarLintGlobalSettings(globalSettings)
        proposed.setServerConnections(listOf(replacement))

        SonarLintGlobalConfigurable.saveSettings(globalSettings, proposed)

        assertThat(globalSettings.serverConnections).containsExactly(replacement)
        assertThat(PasswordSafe.instance.getToken(connection.name)).isEqualTo("test-token")
    }

    @Test
    fun `failed credential deletion keeps settings and draft for retry`() {
        val configurable = createConfigurable()
        val credentialsService = mock<CredentialsService>()
        doThrow(IllegalStateException("Store unavailable")).whenever(credentialsService).withRemovedCredentials(any(), any())
        replaceApplicationService(CredentialsService::class.java, credentialsService)
        try {
            removeConnection(configurable)

            val failure = assertThrows<ConfigurationException> { configurable.apply() }

            assertThat(failure.message).contains("Could not apply connection changes")
            assertThat(globalSettings.serverConnections).containsExactly(connection)
            assertThat(configurable.currentConnections).isEmpty()
            assertThat(configurable.isModified).isTrue()
        } finally {
            configurable.disposeUIResources()
        }
    }

    @Test
    fun `failed settings save restores credentials and keeps draft for retry`() {
        val configurable = createConfigurable()
        val savedSettings = globalSettings
        val store = mock<SonarLintGlobalSettingsStore>()
        whenever(store.state).thenReturn(savedSettings)
        doThrow(IllegalStateException("Settings store unavailable")).doNothing().whenever(store).save(any())
        replaceApplicationService(SonarLintGlobalSettingsStore::class.java, store)
        try {
            removeConnection(configurable)

            assertThrows<ConfigurationException> { configurable.apply() }

            assertThat(globalSettings.serverConnections).containsExactly(connection)
            assertThat(PasswordSafe.instance.getToken(connection.name)).isEqualTo("test-token")
            assertThat(configurable.currentConnections).isEmpty()
            assertThat(configurable.isModified).isTrue()
        } finally {
            configurable.disposeUIResources()
        }
    }

    private fun createConfigurable(): SonarLintGlobalConfigurable {
        globalSettings.setServerConnections(listOf(connection))
        PasswordSafe.instance.setToken(connection.name, "test-token")
        return SonarLintGlobalConfigurable().also {
            it.createComponent()
            it.reset()
        }
    }

    private fun removeConnection(configurable: SonarLintGlobalConfigurable) {
        val field = SonarLintGlobalConfigurable::class.java.getDeclaredField("connectionsPanel")
        field.isAccessible = true
        val panel = field.get(configurable) as ServerConnectionMgmtPanel
        val actionClass = ServerConnectionMgmtPanel::class.java.declaredClasses.single { it.simpleName == "RemoveServerAction" }
        val constructor = actionClass.getDeclaredConstructor(ServerConnectionMgmtPanel::class.java)
        constructor.isAccessible = true
        (constructor.newInstance(panel) as AnActionButtonRunnable).run(null)
        assertThat(configurable.currentConnections).isEmpty()
        assertThat(PasswordSafe.instance.getToken(connection.name)).isEqualTo("test-token")
    }
}
