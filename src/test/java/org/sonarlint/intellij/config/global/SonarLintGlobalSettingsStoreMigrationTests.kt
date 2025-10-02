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
package org.sonarlint.intellij.config.global

import com.intellij.configurationStore.StoreUtil
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.global.credentials.eraseToken
import org.sonarlint.intellij.config.global.credentials.eraseUsernamePassword
import org.sonarlint.intellij.config.global.credentials.getToken
import org.sonarlint.intellij.config.global.credentials.getUsernamePassword

class SonarLintGlobalSettingsStoreMigrationTests : AbstractSonarLintLightTests() {

    companion object {

        @JvmStatic
        @AfterAll
        fun deleteConfigFile() {
            PathManager.getOptionsFile("sonarlint").delete()
        }


        @JvmStatic
        @AfterAll
        fun eraseDataInPasswordSafe() {
            val passwordSafe = PasswordSafe.instance
            passwordSafe.eraseToken("onlyToken")
            passwordSafe.eraseToken("allCredentials")
            passwordSafe.eraseUsernamePassword("onlyLoginPass")
            passwordSafe.eraseUsernamePassword("allCredentials")
        }
    }

    // to avoid wiping out settings in AbstractSonarLintLightTests.beforeEach
    override fun getGlobalSettings(): SonarLintGlobalSettings? {
        return mock(SonarLintGlobalSettings::class.java)
    }

    @Test
    fun `should migrate credentials to credentials store`() {
        val tested = SonarLintUtils.getService(SonarLintGlobalSettingsStore::class.java)
        val settings = SonarLintGlobalSettings()
        settings.serverConnections = storedConnections()
        tested.loadState(settings)

        tested.initializeComponent()

        val passwordSafe = PasswordSafe.instance
        assertThat(passwordSafe.getToken("noCredentials")).isNull()
        assertThat(passwordSafe.getToken("onlyToken")).isEqualTo("onlyTokenToken")
        assertThat(passwordSafe.getToken("onlyLoginPass")).isNull()
        assertThat(passwordSafe.getToken("allCredentials")).isEqualTo("allCredentialsToken")

        assertThat(passwordSafe.getUsernamePassword("noCredentials")).isNull()
        assertThat(passwordSafe.getUsernamePassword("onlyToken")).isNull()
        assertThat(passwordSafe.getUsernamePassword("onlyLoginPass"))
            .isEqualTo(Credentials("onlyLoginPassLogin", "onlyLoginPassPassword"))
        assertThat(passwordSafe.getUsernamePassword("allCredentials"))
            .isEqualTo(Credentials("allCredentialsLogin", "allCredentialsPassword"))
    }

    @Test
    fun `should have connections interface the same order after migration`() {
        val tested = SonarLintUtils.getService(SonarLintGlobalSettingsStore::class.java)
        val settings = SonarLintGlobalSettings()
        settings.serverConnections = storedConnections()
        tested.loadState(settings)

        tested.initializeComponent()

        assertThat(tested.state?.serverConnections)
            .isNotNull()
            .containsExactly(*connectionsWithoutCredentials())
    }

    @Test
    fun `should save a file without sensitive data after migration`() {
        val tested = SonarLintUtils.getService(SonarLintGlobalSettingsStore::class.java)

        val settings = SonarLintGlobalSettings()
        settings.serverConnections = storedConnections()
        tested.loadState(settings)

        tested.initializeComponent()
        StoreUtil.saveSettings(ApplicationManager.getApplication())

        assertThat(PathManager.getOptionsFile("sonarlint"))
            .exists()
            .content()
            .doesNotContain("password")
            .doesNotContain("token")
            .contains("<option name=\"name\" value=\"onlyToken\" />")
            .contains("<option name=\"name\" value=\"onlyLoginPass\" />")
            .contains("<option name=\"name\" value=\"allCredentials\" />")
            .contains("<option name=\"name\" value=\"noCredentials\" />")
    }

    private fun connectionsWithoutCredentials(): Array<ServerConnection> = arrayOf(
        ServerConnection.newBuilder()
            .setName("onlyToken")
            .setHostUrl("host")
            .build(),
        ServerConnection.newBuilder()
            .setName("onlyLoginPass")
            .setHostUrl("host")
            .build(),
        ServerConnection.newBuilder()
            .setName("allCredentials")
            .setHostUrl("host")
            .build(),
        ServerConnection.newBuilder()
            .setName("noCredentials")
            .setRegion("region")
            .setHostUrl("host")
            .setEnableProxy(true)
            .setOrganizationKey("organizationKey")
            .setDisableNotifications(true)
            .build(),
    )

    private fun storedConnections(): MutableList<ServerConnection> = mutableListOf(
        ServerConnection.newBuilder()
            .setName("onlyToken")
            .setHostUrl("host")
            .setToken("dfc5dfc4dfc6dfd3dffedfc5dfc1dfcfdfc4dffedfc5dfc1dfcfdfc4")
            .build(),
        ServerConnection.newBuilder()
            .setName("onlyLoginPass")
            .setHostUrl("host")
            .setLogin("onlyLoginPassLogin")
            .setPassword("dfc5dfc4dfc6dfd3dfe6dfc5dfcddfc3dfc4dffadfcbdfd9dfd9dffadfcbdfd9dfd9dfdddfc5dfd8dfce")
            .build(),
        ServerConnection.newBuilder()
            .setName("allCredentials")
            .setHostUrl("host")
            .setLogin("allCredentialsLogin")
            .setPassword("dfcbdfc6dfc6dfe9dfd8dfcfdfcedfcfdfc4dfdedfc3dfcbdfc6dfd9dffadfcbdfd9dfd9dfdddfc5dfd8dfce")
            .setToken("dfcbdfc6dfc6dfe9dfd8dfcfdfcedfcfdfc4dfdedfc3dfcbdfc6dfd9dffedfc5dfc1dfcfdfc4")
            .build(),
        ServerConnection.newBuilder()
            .setName("noCredentials")
            .setRegion("region")
            .setHostUrl("host")
            .setEnableProxy(true)
            .setOrganizationKey("organizationKey")
            .setDisableNotifications(true)
            .build(),
    )
}
