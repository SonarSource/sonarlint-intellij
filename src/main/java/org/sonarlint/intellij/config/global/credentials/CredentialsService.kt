/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import com.intellij.openapi.components.Service
import java.util.Objects
import org.apache.commons.lang3.BooleanUtils
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.messages.CredentialsChangeListener
import org.sonarlint.intellij.util.computeOnPooledThread
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto

@Service(Service.Level.APP)
class CredentialsService {

    @Throws(CredentialsException::class)
    fun getCredentials(connection: ServerConnection): Either<TokenDto, UsernamePasswordDto> {
        val result = computeOnPooledThread("Getting credentials from store...") {
            readCredentials(connection)
        }
        if (result == null) {
            throw CredentialsException(
                """
                    Failed to get saved credentials for connection '${connection.name}'.
                    This may be caused by an issue with your system's credential storage.
                    Check your IDE's password storage settings in Settings > Appearance & Behavior > System Settings > Passwords."""
            )
        }
        return result
    }

    @Throws(CredentialsException::class)
    private fun readCredentials(connection: ServerConnection): Either<TokenDto, UsernamePasswordDto> {
        val token = PasswordSafe.instance.getToken(connection.name)
        if (token != null) {
            return Either.forLeft(TokenDto(token))
        }

        val credentials = PasswordSafe.instance.getUsernamePassword(connection.name)
        val password = credentials?.getPasswordAsString()
        if (credentials?.userName != null && password != null) {
            return Either.forRight(UsernamePasswordDto(credentials.userName!!, password))
        }

        throw CredentialsException(
            """
                    Could not load token or login/password credentials for connection '${connection.name}'.
                    As a workaround, try removing and re-adding the connection.
                    This may also be caused by an issue with your system's credential storage.
                    Check your IDE's password storage settings in Settings > Appearance & Behavior > System Settings > Passwords."""
        )
    }

    @Throws(CredentialsException::class)
    fun saveCredentials(connectionName: String, credentials: Either<TokenDto, UsernamePasswordDto>) {
        val success = computeOnPooledThread("Saving credentials...") {
            writeCredentials(credentials, connectionName)
        }

        if (BooleanUtils.isNotTrue(success)) {
            throw CredentialsException(
                """
                    Could not save token credentials for connection '$connectionName'.
                    This may be caused by an issue with your system's credential storage.
                    Check your IDE's password storage settings in Settings > Appearance & Behavior > System Settings > Passwords."""
            )
        }
    }

    private fun writeCredentials(
        credentials: Either<TokenDto, UsernamePasswordDto>,
        connectionName: String,
    ): Boolean {
        val passwordSafe = PasswordSafe.instance
        var isEdit: Boolean
        if (credentials.isLeft) {
            val token = passwordSafe.getToken(connectionName)
            isEdit = token != null && token != credentials.left.token
            passwordSafe.setToken(connectionName, credentials.left.token)
        } else if (credentials.isRight) {
            val old = passwordSafe.getUsernamePassword(connectionName)
            val new = credentials.right
            isEdit = old != null
                && (!Objects.equals(old.userName, new.username)
                || !Objects.equals(old.password, new.password))
            passwordSafe.setUsernamePassword(
                connectionName,
                new.username, new.password)
        } else {
            return false
        }

        if (isEdit) {
            ApplicationManager.getApplication().messageBus.syncPublisher(CredentialsChangeListener.TOPIC)
                .onCredentialsChanged(connectionName)
        }
        return true
    }

    fun eraseCredentials(connection: ServerConnection) {
        val passwordSafe = PasswordSafe.instance
        passwordSafe.eraseToken(connection.name)
        passwordSafe.eraseUsernamePassword(connection.name)
    }

    fun saveDogfoodCredentials(username: String?, pass: String?) {
        PasswordSafe.instance.setDogfoodUsernamePassword(username, pass)
    }

    fun getDogfoodCredentials(): Credentials? = PasswordSafe.instance.getDogfoodCredentials()
}
