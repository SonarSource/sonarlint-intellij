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
package org.sonarlint.intellij.config.global.wizard

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.config.global.SonarCloudConnection

internal class WizardModelTests {
    @Test
    fun testCreateFromConfig() {
        val connection = SonarCloudConnection("name", "token", "org", false)
        val model = WizardModel(connection)
        assertThat(model.organizationKey).isEqualTo("org")
        assertThat(model.organizationList).isEmpty()
        assertThat(model.name).isEqualTo("name")
        assertThat(model.serverUrl).isEqualTo("https://sonarcloud.io")
    }

    @Test
    fun testExportSonarQubeToConfig() {
        val model = WizardModel()
        model.setName("name")
        model.setIsSonarQube("url")
        model.setLoginPassword("login", charArrayOf('p', 'a', 's', 's'))

        val connection = model.createConnection()
        assertThat(connection.hostUrl).isEqualTo("url")
        assertThat(connection.credentials.login).isEqualTo("login")
        assertThat(connection.credentials.password).isEqualTo("pass")
        assertThat(connection.credentials.token).isNull()
    }

    @Test
    fun testExportSonarCloud() {
        val model = WizardModel()
        model.setName("name")
        model.setIsSonarCloud()
        model.setOrganizationKey("org")
        model.setToken("token")

        val connection = model.createConnection()
        assertThat(connection.hostUrl).isEqualTo("https://sonarcloud.io")
        assertThat((connection as SonarCloudConnection).organizationKey).isEqualTo("org")
        assertThat(connection.credentials.token).isEqualTo("token")
        assertThat(connection.credentials.login).isNull()
        assertThat(connection.credentials.password).isNull()
    }
}
