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

import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.global.credentials.eraseToken
import org.sonarlint.intellij.config.global.credentials.eraseUsernamePassword
import org.sonarlint.intellij.config.global.credentials.getToken
import org.sonarlint.intellij.config.global.credentials.getUsernamePassword
import org.sonarlint.intellij.fixtures.AbstractLightTests

class SonarLintGlobalSettingsStoreMigrationTests : AbstractLightTests() {

    companion object {

        @JvmStatic
        @BeforeAll
        fun setUpConfigFile() {
            val sonarlintConfigFile = PathManager.getOptionsFile("sonarlint")
            val testFile = SonarLintGlobalSettingsStoreMigrationTests::class.java
                .getResourceAsStream("/options/sonarlint.xml")!!
            Files.copy(testFile, sonarlintConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

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

    @Test
    fun `should migrate credentials to credentials store`() {
        val serverConnections = SonarLintUtils.getService(SonarLintGlobalSettingsStore::class.java)
            ?.state?.serverConnections

        val passwordSafe = PasswordSafe.instance
        await().during(1000, TimeUnit.MILLISECONDS).untilAsserted {
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

            assertThat(serverConnections)
                .isNotNull()
                .contains(*connectionsWithoutCredentials())
        }
    }

    @Test
    fun `should have connections interface the same order after migration`() {
        await().during(1000, TimeUnit.MILLISECONDS).untilAsserted {
            val serverConnections = SonarLintUtils.getService(SonarLintGlobalSettingsStore::class.java)
                ?.state?.serverConnections

            assertThat(serverConnections)
                .isNotNull()
                .containsExactly(*connectionsWithoutCredentials())
        }
    }

    @Test
    fun `should save a file without sensitive data after migration`() {
        await().during(1000, TimeUnit.MILLISECONDS).untilAsserted {
            assertThat(PathManager.getOptionsFile("sonarlint"))
                .content()
                .doesNotContain("password")
                .doesNotContain("token")
                .contains("<option name=\"name\" value=\"onlyToken\" />")
                .contains("<option name=\"name\" value=\"onlyLoginPass\" />")
                .contains("<option name=\"name\" value=\"allCredentials\" />")
                .contains("<option name=\"name\" value=\"noCredentials\" />")
        }
    }

    private fun connectionsWithoutCredentials(): Array<out ServerConnection?> = arrayOf(
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
}
