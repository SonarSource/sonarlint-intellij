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
package org.sonarlint.intellij.fixtures

import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.ServerConnectionCredentials
import org.sonarlint.intellij.config.global.SonarCloudConnection
import org.sonarlint.intellij.config.global.SonarQubeConnection
import org.sonarlint.intellij.config.global.wizard.PartialConnection
import org.sonarlint.intellij.core.SonarProduct

fun newSonarQubeConnection(name: String = "id", hostUrl: String = "https://host"): ServerConnection {
    return SonarQubeConnection(name, hostUrl, true)
}

fun newSonarCloudConnection(name: String, organizationKey: String): ServerConnection {
    return SonarCloudConnection(name, organizationKey, true)
}

fun newPartialConnection(serverUrl: String = "https://serverUrl") = PartialConnection(serverUrl, SonarProduct.SONARQUBE, "orgKey", ServerConnectionCredentials(null, null, "token"))