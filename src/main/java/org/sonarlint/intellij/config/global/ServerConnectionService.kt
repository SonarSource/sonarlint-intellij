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
import com.intellij.util.ui.EDT
import java.util.Optional
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.messages.ServerConnectionsListener
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarlint.intellij.util.runOnPooledThread

@Service(Service.Level.APP)
class ServerConnectionService {

    init {
        loadAndMigrateServerConnectionsAsync()
    }

    private fun loadAndMigrateServerConnectionsAsync() {
        runOnPooledThread { getGlobalSettings().serverConnections.forEach { migrate(it) } }
    }

    private fun migrate(connection: ServerConnectionSettings) {
        saveCredentials(connection.name, ServerConnectionCredentials(connection.login, connection.password, connection.token))
        connection.clearCredentials()
    }

    fun getConnections(): List<ServerConnection> = getGlobalSettings().serverConnections.filter { isValid(it) }.mapNotNull { connection ->
        if (connection.isSonarCloud) {
            SonarCloudConnection(connection.name, connection.organizationKey!!, connection.isDisableNotifications)
        } else SonarQubeConnection(connection.name, connection.hostUrl, connection.isDisableNotifications)
    }

    private fun getConnectionsWithAuth(): List<ServerConnectionWithAuth> = getConnections().map { ServerConnectionWithAuth(it, loadCredentials(it.name)) }

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

    fun getServerConnectionWithAuthByName(name: String): Optional<ServerConnectionWithAuth> {
        return Optional.ofNullable(getConnectionsWithAuth().firstOrNull { name == it.connection.name })
    }

    fun getServerCredentialsByName(name: String): Optional<ServerConnectionCredentials> {
        return Optional.ofNullable(loadCredentials(name))
    }

    fun connectionExists(connectionName: String): Boolean {
        return getConnections().any { it.name == connectionName }
    }

    fun getServerNames(): Set<String> {
        return getConnections().map { it.name }.toSet()
    }

    fun addServerConnection(connection: ServerConnectionWithAuth) {
        updateServerConnections(getGlobalSettings(), emptySet(), emptyList(), listOf(connection))
    }

    fun replaceConnection(replacementConnection: ServerConnectionWithAuth) {
        updateServerConnections(getGlobalSettings(), emptySet(), listOf(replacementConnection), emptyList())
    }

    fun updateServerConnections(settings: SonarLintGlobalSettings, deletedConnectionNames: Set<String>, updatedConnectionsWithAuth: Collection<ServerConnectionWithAuth>, addedConnectionsWithAuth: Collection<ServerConnectionWithAuth>) {
        // save credentials
        deletedConnectionNames.forEach { forgetCredentials(it) }
        updatedConnectionsWithAuth.forEach { saveCredentials(it.connection.name, it.credentials) }
        addedConnectionsWithAuth.forEach { saveCredentials(it.connection.name, it.credentials) }

        // save connections
        val currentlySavedConnections = settings.serverConnections.toMutableList()
        currentlySavedConnections.removeAll { deletedConnectionNames.contains(it.name) }
        updatedConnectionsWithAuth.map { it.connection }.forEach { updatedConnection ->
            currentlySavedConnections[currentlySavedConnections.indexOfFirst { it.name == updatedConnection.name }] = toSettings(updatedConnection)
        }
        currentlySavedConnections.addAll(addedConnectionsWithAuth.map { it.connection }.map { toSettings(it) })
        settings.serverConnections = currentlySavedConnections.toList()

        // notify
        notifyConnectionsChange(getConnections())
        notifyCredentialsChange(updatedConnectionsWithAuth.map { it.connection })
    }

    private fun toSettings(serverConnection: ServerConnection): ServerConnectionSettings {
        return with(serverConnection) {
            var builder = ServerConnectionSettings.newBuilder().setName(this.name).setHostUrl(this.hostUrl).setDisableNotifications(this.notificationsDisabled)
            if (this is SonarCloudConnection) {
                builder = builder.setOrganizationKey(this.organizationKey)
            }
            builder.build()
        }
    }

    private fun notifyConnectionsChange(connections: List<ServerConnection>) {
        ApplicationManager.getApplication().messageBus.syncPublisher(ServerConnectionsListener.TOPIC).afterChange(connections)
    }

    private fun notifyCredentialsChange(serverConnections: List<ServerConnection>) {
        ApplicationManager.getApplication().messageBus.syncPublisher(ServerConnectionsListener.TOPIC).credentialsChanged(serverConnections)
    }

    private fun loadCredentials(connectionName: String): ServerConnectionCredentials {
        // loading credentials is a slow operation
        check(!EDT.isCurrentThreadEdt()) { "Cannot load credentials from EDT" }
        val token = PasswordSafe.instance.getPassword(tokenCredentials(connectionName))
        if (token != null) {
            return ServerConnectionCredentials(null, null, token)
        }
        val loginPassword = PasswordSafe.instance[loginPasswordCredentials(connectionName)]
        if (loginPassword != null) {
            return ServerConnectionCredentials(loginPassword.userName, loginPassword.password.toString(), null)
        }
        throw ServerConnectionCredentialsNotFound(connectionName)
    }

    private fun saveCredentials(connectionId: String, credentials: ServerConnectionCredentials) {
        // saving credentials is a slow operation
        check(!EDT.isCurrentThreadEdt()) { "Cannot save credentials from EDT" }
        if (credentials.token != null) {
            PasswordSafe.instance.setPassword(tokenCredentials(connectionId), credentials.token)
        } else if (credentials.login != null && credentials.password != null) {
            PasswordSafe.instance[loginPasswordCredentials(connectionId)] = Credentials(credentials.login, credentials.password)
        }
        // else probably already migrated
    }

    private fun forgetCredentials(connectionId: String) {
        // saving credentials is a slow operation
        check(!EDT.isCurrentThreadEdt()) { "Cannot save credentials from EDT" }
        PasswordSafe.instance[tokenCredentials(connectionId)] = null
    }

    companion object {
        @JvmStatic
        fun getInstance(): ServerConnectionService = getService(ServerConnectionService::class.java)
        private fun tokenCredentials(connectionId: String) = CredentialAttributes(generateServiceName("SonarLint connections", "$connectionId.token"))
        private fun loginPasswordCredentials(connectionId: String) = CredentialAttributes(generateServiceName("SonarLint connections", "$connectionId.loginPassword"))
    }
}