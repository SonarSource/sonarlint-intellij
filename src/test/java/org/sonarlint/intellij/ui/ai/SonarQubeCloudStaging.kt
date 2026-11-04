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
package org.sonarlint.intellij.ui.ai

import org.sonarlint.intellij.common.util.SonarLintUtils.SONARCLOUD_URL
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.core.BackendService
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto

/**
 * SonarQube Cloud staging, which the build makes the tests' SonarQube Cloud (see `sonarlint.internal.sonarcloud.url` in
 * build.gradle.kts), and the integration tests' organization and token there.
 */
object SonarQubeCloudStaging {
    const val URL = "https://sc-staging.io"
    const val ORGANIZATION = "sonarlint-it"
    private const val CONNECTION_NAME = "sonarqube-cloud-staging"

    /** The integration tests' token, `SONARCLOUD_IT_TOKEN`; null on a developer machine without it. */
    val token: String? = System.getenv("SONARCLOUD_IT_TOKEN")?.takeIf { it.isNotBlank() }

    /** Adds the IDE connection a user would have created in the settings, with [token] saved in the credential store. */
    fun addConnection(token: String) {
        val connection = ServerConnection.newBuilder().setName(CONNECTION_NAME).setHostUrl(SONARCLOUD_URL).setOrganizationKey(ORGANIZATION).build()
        getService(CredentialsService::class.java).saveCredentials(CONNECTION_NAME, Either.forLeft(TokenDto(token)))
        getGlobalSettings().serverConnections = listOf(connection)
        getService(BackendService::class.java).connectionsUpdated(listOf(connection))
    }

    /** Removes the connection's token from the credential store; light tests remove the connection itself before each test. */
    fun removeConnectionToken() {
        getGlobalSettings().getServerConnectionByName(CONNECTION_NAME).ifPresent {
            getService(CredentialsService::class.java).eraseCredentials(it)
        }
    }
}
