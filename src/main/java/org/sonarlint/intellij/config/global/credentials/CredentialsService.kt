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
package org.sonarlint.intellij.config.global.credentials

import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import java.util.Objects
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.messages.CredentialsChangeListener
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto

@Service(Service.Level.APP)
class CredentialsService @JvmOverloads constructor(private val passwordSafe: () -> PasswordSafe = { PasswordSafe.instance }) {

    @Throws(CredentialsException::class)
    fun getCredentials(connection: ServerConnection): Either<TokenDto, UsernamePasswordDto> =
        await(connection.name) { cancellation -> getCredentials(connection, cancellation) }

    @Throws(CredentialsException::class)
    fun saveCredentials(connectionName: String, credentials: Either<TokenDto, UsernamePasswordDto>) {
        await(connectionName) { cancellation -> saveCredentials(connectionName, credentials, cancellation) }
    }

    fun eraseCredentials(connection: ServerConnection) {
        await(connection.name) { cancellation -> eraseCredentials(connection, cancellation) }
    }

    internal fun getCredentials(connection: ServerConnection, cancellation: CredentialCancellation) =
        operations.submit(connection.name, cancellation) {
            val safe = passwordSafe()
            cancellation.checkCancelled()
            val token = safe.getToken(connection.name)
            cancellation.checkCancelled()
            if (token != null) {
                Either.forLeft<TokenDto, UsernamePasswordDto>(TokenDto(token))
            } else {
                val credentials = safe.getUsernamePassword(connection.name)
                cancellation.checkCancelled()
                val password = credentials?.getPasswordAsString()
                if (credentials?.userName != null && password != null) {
                    Either.forRight<TokenDto, UsernamePasswordDto>(UsernamePasswordDto(credentials.userName!!, password))
                } else {
                    throw CredentialsException("Could not load token or login/password credentials for connection '${connection.name}'. " +
                        "As a workaround, try removing and re-adding the connection. " + storageAdvice)
                }
            }
        }

    internal fun saveCredentials(name: String, credentials: Either<TokenDto, UsernamePasswordDto>, cancellation: CredentialCancellation) =
        operations.submit(name, cancellation) {
            val safe = passwordSafe()
            cancellation.checkCancelled()
            val changed: Boolean
            if (credentials.isLeft) {
                val token = safe.getToken(name)
                cancellation.checkCancelled()
                changed = token != null && token != credentials.left.token
                cancellation.checkCancelled()
                safe.setToken(name, credentials.left.token)
            } else if (credentials.isRight) {
                val old = safe.getUsernamePassword(name)
                cancellation.checkCancelled()
                val new = credentials.right
                changed = old != null && (!Objects.equals(old.userName, new.username) || !Objects.equals(old.getPasswordAsString(), new.password))
                cancellation.checkCancelled()
                safe.setUsernamePassword(name, new.username, new.password)
            } else {
                throw CredentialsException("Could not save credentials for connection '$name'. " + storageAdvice)
            }
            // A provider may ignore interruption after entering set. Publish actual changes even if its caller has cancelled.
            if (changed) {
                ApplicationManager.getApplication().messageBus.syncPublisher(CredentialsChangeListener.TOPIC).onCredentialsChanged(name)
            }
        }

    internal fun eraseCredentials(connection: ServerConnection, cancellation: CredentialCancellation) =
        operations.submit(connection.name, cancellation) {
            val safe = passwordSafe()
            cancellation.checkCancelled()
            safe.eraseToken(connection.name)
            cancellation.checkCancelled()
            safe.eraseUsernamePassword(connection.name)
        }

    private fun <T> await(name: String, submit: (CredentialCancellation) -> CompletableFuture<T>): T {
        val cancellation = CredentialCancellation()
        try {
            return submit(cancellation).get(30, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw CredentialsException("Credential operation failed for connection '$name'. ${e.cause?.message ?: storageAdvice}")
        } catch (e: Exception) {
            cancellation.cancel()
            if (e is InterruptedException) Thread.currentThread().interrupt()
            throw CredentialsException("Credential operation failed for connection '$name'. ${e.message ?: storageAdvice}")
        }
    }

    companion object {
        private val operations = CredentialOperationQueue()
        private const val storageAdvice = "This may be caused by an issue with your system's credential storage. " +
            "Check your IDE's password storage settings in Settings > Appearance & Behavior > System Settings > Passwords."
    }
}
