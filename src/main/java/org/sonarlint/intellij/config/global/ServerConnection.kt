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

import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.common.util.SonarLintUtils.SONARCLOUD_URL
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.SonarProduct
import org.sonarlint.intellij.core.server.ServerLinks
import org.sonarlint.intellij.core.server.SonarCloudLinks
import org.sonarlint.intellij.core.server.SonarQubeLinks
import org.sonarsource.sonarlint.core.serverapi.EndpointParams
import org.sonarsource.sonarlint.core.serverapi.ServerApi


sealed class ServerConnection {
    abstract val name: String
    abstract val notificationsDisabled: Boolean
    abstract val hostUrl: String
    abstract val product: SonarProduct
    abstract val links: ServerLinks
    abstract val endpointParams: EndpointParams
    fun api() = ServerApi(endpointParams, SonarLintUtils.getService(BackendService::class.java).getHttpClient(name))
    override fun toString() = name
    val isSonarCloud get() = product == SonarProduct.SONARCLOUD
    val isSonarQube get() = product == SonarProduct.SONARQUBE
    val productName get() = product.productName
}

data class SonarQubeConnection(override val name: String, override val hostUrl: String, override val notificationsDisabled: Boolean) : ServerConnection() {
    override val product = SonarProduct.SONARQUBE
    override val links = SonarQubeLinks(hostUrl)
    override val endpointParams = EndpointParams(hostUrl, false, null)
}

data class SonarCloudConnection(override val name: String, val organizationKey: String, override val notificationsDisabled: Boolean) : ServerConnection() {
    override val product = SonarProduct.SONARCLOUD
    override val links = SonarCloudLinks
    override val hostUrl: String = SONARCLOUD_URL
    override val endpointParams = EndpointParams(hostUrl, true, organizationKey)
}
