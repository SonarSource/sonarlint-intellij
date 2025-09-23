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
package org.sonarlint.intellij.config.global.credentials

import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.MessageBusConnection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.messages.CredentialsChangeListener
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto

private const val CONNECTION_NAME = "name"
private const val EXPECTED_USER = "sonar"
private const val EXPECTED_PASSWORD = "password"
private const val EXPECTED_TOKEN = "token"
private val TEST_CONNECTION = ServerConnection.newBuilder()
    .setName(CONNECTION_NAME)
    .build()

class CredentialsServiceTests : AbstractSonarLintLightTests() {

    private val tested = CredentialsService()

    private lateinit var updatedConnectionIds: MutableList<String>
    private lateinit var busConnection: MessageBusConnection

    @BeforeEach
    fun beforeEach() {
        updatedConnectionIds = ArrayList()
        busConnection = ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
        busConnection.subscribe(CredentialsChangeListener.TOPIC, object : CredentialsChangeListener {
            override fun onCredentialsChanged(connectionId: String) {
                updatedConnectionIds.add(connectionId)
            }
        })
    }

    @AfterEach
    fun afterEach() {
        PasswordSafe.instance.eraseToken(CONNECTION_NAME)
        PasswordSafe.instance.eraseUsernamePassword(CONNECTION_NAME)
        busConnection.disconnect()
    }

    @Test
    fun `should get token from credentials store`() {
        PasswordSafe.instance.setToken(CONNECTION_NAME, EXPECTED_TOKEN)

        val actual = tested.getCredentials(TEST_CONNECTION)

        assertThat(actual.isLeft).isTrue()
        assertThat(actual.left).isEqualTo(TokenDto(EXPECTED_TOKEN))
    }

    @Test
    fun `should get username and password from credentials store`() {
        PasswordSafe.instance.setUsernamePassword(CONNECTION_NAME,
            Credentials(EXPECTED_USER, EXPECTED_PASSWORD))

        val actual = tested.getCredentials(TEST_CONNECTION)

        assertThat(actual.isRight).isTrue()
        assertThat(actual.right).isEqualTo(UsernamePasswordDto(EXPECTED_USER, EXPECTED_PASSWORD))
    }

    @Test
    fun `should get token when both are available`() {
        PasswordSafe.instance.setToken(CONNECTION_NAME, EXPECTED_TOKEN)
        PasswordSafe.instance.setUsernamePassword(CONNECTION_NAME,
            Credentials(EXPECTED_USER, EXPECTED_PASSWORD))

        val actual = tested.getCredentials(TEST_CONNECTION)

        assertThat(actual.isLeft).isTrue()
        assertThat(actual.left).isEqualTo(TokenDto(EXPECTED_TOKEN))
    }

    @Test
    fun `should throw exception when there are no credentials`() {
        assertThrows<CredentialsException> {
            tested.getCredentials(TEST_CONNECTION)
        }
    }

    @Test
    fun `should set token to credential store`() {
        tested.saveCredentials(
            CONNECTION_NAME,
            Either.forLeft(TokenDto(EXPECTED_TOKEN))
        )
        val token = PasswordSafe.instance.getToken(CONNECTION_NAME)

        assertThat(token).isEqualTo(EXPECTED_TOKEN)
        assertThat(updatedConnectionIds).isEmpty()
    }

    @Test
    fun `should set username and password to credentials store`() {
        tested.saveCredentials(
            CONNECTION_NAME,
            Either.forRight(UsernamePasswordDto(EXPECTED_USER, EXPECTED_PASSWORD))
        )
        val userNamePassword = PasswordSafe.instance.getUsernamePassword(CONNECTION_NAME)

        assertThat(userNamePassword).isEqualTo(Credentials(EXPECTED_USER, EXPECTED_PASSWORD))
        assertThat(updatedConnectionIds).isEmpty()
    }

    @Test
    fun `should throw exception when trying to set empty credentials`() {
        assertThrows<CredentialsException> {
            tested.saveCredentials(CONNECTION_NAME, Either.forLeft(null))
        }
        assertThat(updatedConnectionIds).isEmpty()
    }

    @Test
    fun `should fire an event when token is updated`() {
        PasswordSafe.instance.setToken(CONNECTION_NAME, "oldToken")

        tested.saveCredentials(
            CONNECTION_NAME,
            Either.forLeft(TokenDto(EXPECTED_TOKEN))
        )
        val token = PasswordSafe.instance.getToken(CONNECTION_NAME)

        assertThat(token).isEqualTo(EXPECTED_TOKEN)
        assertThat(updatedConnectionIds).containsExactly(CONNECTION_NAME)
    }

    @Test
    fun `should fire an event when username is updated`() {
        PasswordSafe.instance.setUsernamePassword(CONNECTION_NAME,
            Credentials("oldUser", EXPECTED_PASSWORD))

        tested.saveCredentials(
            CONNECTION_NAME,
            Either.forRight(UsernamePasswordDto(EXPECTED_USER, EXPECTED_PASSWORD))
        )
        val userNamePassword = PasswordSafe.instance.getUsernamePassword(CONNECTION_NAME)

        assertThat(userNamePassword).isEqualTo(Credentials(EXPECTED_USER, EXPECTED_PASSWORD))
        assertThat(updatedConnectionIds).containsExactly(CONNECTION_NAME)
    }

    @Test
    fun `should fire an event when password is updated`() {
        PasswordSafe.instance.setUsernamePassword(CONNECTION_NAME,
            Credentials(EXPECTED_USER, "oldPassword"))

        tested.saveCredentials(
            CONNECTION_NAME,
            Either.forRight(UsernamePasswordDto(EXPECTED_USER, EXPECTED_PASSWORD))
        )
        val userNamePassword = PasswordSafe.instance.getUsernamePassword(CONNECTION_NAME)

        assertThat(userNamePassword).isEqualTo(Credentials(EXPECTED_USER, EXPECTED_PASSWORD))
        assertThat(updatedConnectionIds).containsExactly(CONNECTION_NAME)
    }

    @Test
    fun `should not fire an event when username and password is changed`() {
        PasswordSafe.instance.setUsernamePassword(CONNECTION_NAME,
            Credentials(EXPECTED_USER, EXPECTED_PASSWORD))

        tested.saveCredentials(
            CONNECTION_NAME,
            Either.forRight(UsernamePasswordDto(EXPECTED_USER, EXPECTED_PASSWORD))
        )
        val userNamePassword = PasswordSafe.instance.getUsernamePassword(CONNECTION_NAME)

        assertThat(userNamePassword).isEqualTo(Credentials(EXPECTED_USER, EXPECTED_PASSWORD))
        assertThat(updatedConnectionIds).isEmpty()
    }

    @Test
    fun `should not fire an event when token not changed`() {
        PasswordSafe.instance.setToken(CONNECTION_NAME, EXPECTED_TOKEN)

        tested.saveCredentials(
            CONNECTION_NAME,
            Either.forLeft(TokenDto(EXPECTED_TOKEN))
        )
        val token = PasswordSafe.instance.getToken(CONNECTION_NAME)

        assertThat(token).isEqualTo(EXPECTED_TOKEN)
        assertThat(updatedConnectionIds).isEmpty()
    }

    @Test
    fun `should erase all associated credentials from store`() {
        PasswordSafe.instance.setToken(CONNECTION_NAME, EXPECTED_TOKEN)
        PasswordSafe.instance.setUsernamePassword(CONNECTION_NAME,
            Credentials(EXPECTED_USER, EXPECTED_PASSWORD))

        tested.eraseCredentials(TEST_CONNECTION)
        val userNamePassword = PasswordSafe.instance.getUsernamePassword(CONNECTION_NAME)
        val token = PasswordSafe.instance.getToken(CONNECTION_NAME)

        assertThat(userNamePassword).isNull()
        assertThat(token).isNull()
    }
}
