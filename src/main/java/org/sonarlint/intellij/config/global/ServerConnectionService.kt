/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import java.util.Optional
import kotlinx.collections.immutable.toImmutableList
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.messages.ServerConnectionsListener
import org.sonarlint.intellij.util.GlobalLogOutput

@Service(Service.Level.APP)
class ServerConnectionService {

    init {
        loadAndMigrateServerConnections()
    }

    private fun loadAndMigrateServerConnections() {
        getGlobalSettings().serverConnections.forEach { migrate(it) }
    }

    private fun migrate(connection: ServerConnectionSettings) {
        saveCredentials(connection.name, ServerConnectionCredentials(connection.login, connection.password, connection.token))
        connection.clearCredentials()
    }

    fun getConnections(): List<ServerConnection> = getGlobalSettings().serverConnections.filter { isValid(it) }.mapNotNull { connection ->
        val credentials = loadCredentials(connection.name) ?: return@mapNotNull null
        if (connection.isSonarCloud) {
            val token = credentials.token ?: run {
                GlobalLogOutput.get().logError("Token not found in secure storage for connection $connection", null)
                return@mapNotNull null
            }
            SonarCloudConnection(connection.name, token, connection.organizationKey!!, connection.isDisableNotifications)
        } else SonarQubeConnection(connection.name, connection.hostUrl, credentials, connection.isDisableNotifications)
    }

    private fun isValid(connectionSettings: ServerConnectionSettings): Boolean {
        val valid = connectionSettings.name != null && if (connectionSettings.isSonarCloud) connectionSettings.organizationKey != null
        else connectionSettings.hostUrl != null
        if (!valid) {
            GlobalLogOutput.get().logError("The connection $connectionSettings is not valid", null)
        }
        return valid
    }

    fun getServerConnectionByName(name: String): Optional<ServerConnection> {
        return Optional.ofNullable(getConnections().firstOrNull { name == it.name })
    }

    fun connectionExists(connectionName: String): Boolean {
        return getConnections().any { it.name == connectionName }
    }

    fun getServerNames(): Set<String> {
        return getConnections().map { it.name }.toSet()
    }

    fun addServerConnection(connection: ServerConnection) {
        setServerConnections(getConnections() + connection)
    }

    fun replaceConnection(name: String, replacementConnection: ServerConnection) {
        val serverConnections = getConnections().toMutableList()
        serverConnections[serverConnections.indexOfFirst { it.name == name }] = replacementConnection
        setServerConnections(serverConnections.toImmutableList())
    }

    private fun setServerConnections(connections: List<ServerConnection>) {
        setServerConnections(getGlobalSettings(), connections)
    }

    fun setServerConnections(settings: SonarLintGlobalSettings, connections: List<ServerConnection>) {
        val previousConnections = getConnections()
        settings.serverConnections = connections.map {
            var builder = ServerConnectionSettings.newBuilder().setName(it.name).setHostUrl(it.hostUrl).setDisableNotifications(it.notificationsDisabled)
            if (it is SonarCloudConnection) {
                builder = builder.setOrganizationKey(it.organizationKey)
            }
            builder.build()
        }.toList()
        connections.forEach { saveCredentials(it.name, it.credentials) }
        notifyConnectionsChange(connections)
        notifyCredentialsChange(previousConnections, connections)
        val removedConnectionNames = previousConnections.map { it.name }.filter { name -> !connections.map { it.name }.contains(name) }
        removedConnectionNames.forEach { forgetCredentials(it) }
    }

    private fun notifyConnectionsChange(connections: List<ServerConnection>) {
        ApplicationManager.getApplication().messageBus.syncPublisher(ServerConnectionsListener.TOPIC).afterChange(connections)
    }

    private fun notifyCredentialsChange(previousConnections: List<ServerConnection>, newConnections: List<ServerConnection>) {
        val changedConnections = newConnections.filter { connection ->
            val previousConnection = previousConnections.find { it.name == connection.name }
            previousConnection?.let { connection.credentials != it.credentials } == true
        }
        if (changedConnections.isNotEmpty()) {
            ApplicationManager.getApplication().messageBus.syncPublisher(ServerConnectionsListener.TOPIC).credentialsChanged(changedConnections)
        }
    }

    private fun loadCredentials(connectionId: String): ServerConnectionCredentials? {
        val token = PasswordSafe.instance.getPassword(tokenCredentials(connectionId))
        if (token != null) {
            return ServerConnectionCredentials(null, null, token)
        }
        val loginPassword = PasswordSafe.instance[loginPasswordCredentials(connectionId)]
        if (loginPassword != null) {
            return ServerConnectionCredentials(loginPassword.userName, loginPassword.password.toString(), null)
        }
        GlobalLogOutput.get().logError("Unable to retrieve credentials from secure storage for connection '$connectionId'", null)
        return null
    }

    private fun saveCredentials(connectionId: String, credentials: ServerConnectionCredentials) {
        if (credentials.token != null) {
            PasswordSafe.instance.setPassword(tokenCredentials(connectionId), credentials.token)
        } else if (credentials.login != null && credentials.password != null) {
            PasswordSafe.instance[loginPasswordCredentials(connectionId)] = Credentials(credentials.login, credentials.password)
        }
        // else probably already migrated
    }

    private fun forgetCredentials(connectionId: String) {
        PasswordSafe.instance[tokenCredentials(connectionId)] = null
    }

    companion object {
        @JvmStatic
        fun getInstance(): ServerConnectionService = getService(ServerConnectionService::class.java)
        private fun tokenCredentials(connectionId: String) = CredentialAttributes(generateServiceName("SonarLint connections", "$connectionId.token"))
        private fun loginPasswordCredentials(connectionId: String) = CredentialAttributes(generateServiceName("SonarLint connections", "$connectionId.loginPassword"))
    }
}