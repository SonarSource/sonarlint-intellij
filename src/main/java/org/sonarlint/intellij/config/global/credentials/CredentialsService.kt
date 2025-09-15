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
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.PasswordUtil
import java.util.Objects
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.dogfood.DogfoodCredentialsStore
import org.sonarlint.intellij.messages.CredentialsChangeListener
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto

@Service(Service.Level.APP)
class CredentialsService {

    fun getCredentials(connection: ServerConnection): Either<TokenDto, UsernamePasswordDto> {
        val token = PasswordSafe.instance.getToken(connection.name)
        if (token != null) {
            return Either.forLeft(TokenDto(token))
        }

        val credentials = PasswordSafe.instance.getUsernamePassword(connection.name)
        val password = credentials?.getPasswordAsString()
        if (credentials?.userName != null && password != null) {
            return Either.forRight(UsernamePasswordDto(credentials.userName!!, password))
        }

        throw CredentialsException("Could not load token or login/password credentials for connection: ${connection.name}")
    }

    fun saveCredentials(connectionName: String, credentials: Either<TokenDto, UsernamePasswordDto>) {
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
                && (!Objects.equals(old.userName,new.username)
                || !Objects.equals(old.password, new.password))
            passwordSafe.setUsernamePassword(
                connectionName,
                new.username, new.password)
        } else {
            throw CredentialsException("Could not save token or login/password credentials for connection: $connectionName")
        }

        if (isEdit) {
            ApplicationManager.getApplication().messageBus.syncPublisher(CredentialsChangeListener.TOPIC)
                .onCredentialsChanged(connectionName)
        }
    }

    fun eraseCredentials(connection: ServerConnection) {
        val passwordSafe = PasswordSafe.instance
        passwordSafe.eraseToken(connection.name)
        passwordSafe.eraseUsernamePassword(connection.name)
    }

    fun migrateCredentials(connection: ServerConnection): ServerConnection {
        if (connection.token != null) {
            PasswordSafe.instance.setToken(connection.name,
                decodeOld(connection.token))
        }
        if (connection.password != null) {
            PasswordSafe.instance.setUsernamePassword(connection.name,
                Credentials(connection.login, decodeOld(connection.password)))
        }
        return ServerConnection.newBuilder()
            .setRegion(connection.region)
            .setHostUrl(connection.hostUrl)
            .setName(connection.name)
            .setEnableProxy(connection.isEnableProxy)
            .setOrganizationKey(connection.organizationKey)
            .setDisableNotifications(connection.isDisableNotifications)
            .build()
    }

    fun saveDogfoodCredentials(username: String?, pass: String?) {
        PasswordSafe.instance.setDogfoodUsernamePassword(username, pass)
    }

    fun getDogfoodCredentials(): Credentials? {
        migrateDogfoodCredentials()

        return PasswordSafe.instance.getDogfoodCredentials()
    }

    @Suppress("DEPRECATION")
    private fun migrateDogfoodCredentials() {
        val oldStore = getService(DogfoodCredentialsStore::class.java)
        val oldCredentials = oldStore.state
        if (oldCredentials != null) {
            migrateDogfoodCredentials(oldCredentials.username, oldCredentials.pass)
            oldStore.erase()
        }
    }

    private fun migrateDogfoodCredentials(username: String?, pass: String?) {
        saveDogfoodCredentials(username, decodeOld(pass))
    }

    @Suppress("DEPRECATION")
    private fun decodeOld(string: String?): String? {
        if (string == null) {
            return null
        }
        return try {
            PasswordUtil.decodePassword(string)
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun hasOldCredentials(connection: ServerConnection): Boolean {
        return connection.login != null
            || connection.password != null
            || connection.token != null
    }
}
