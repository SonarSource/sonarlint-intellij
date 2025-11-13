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
package org.sonarlint.intellij.dogfood

import com.intellij.configurationStore.StoreUtil
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.SonarLintGlobalSettingsStoreMigrationTests
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.config.global.credentials.eraseDogfoodCredentials

class DogFoodCredentialsStoreMigrationTests : AbstractSonarLintLightTests() {

    companion object {

        @JvmStatic
        @BeforeAll
        fun setUpConfigFile() {
            val sonarlintConfigFile = PathManager.getOptionsFile("sonarlint-dogfood")
            val testFile = SonarLintGlobalSettingsStoreMigrationTests::class.java
                .getResourceAsStream("/options/sonarlint-dogfood.xml")!!
            Files.copy(testFile, sonarlintConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        @JvmStatic
        @AfterAll
        fun deleteConfigFile() {
            PathManager.getOptionsFile("sonarlint-dogfood").delete()
        }

        @JvmStatic
        @AfterAll
        fun eraseDataInPasswordSafe() {
            PasswordSafe.instance.eraseDogfoodCredentials()
        }
    }

    @Test
    fun `should migrate dogfood credentials`() {
        val dogfoodCredentials = getService(CredentialsService::class.java).getDogfoodCredentials()

        assertThat(dogfoodCredentials).isEqualTo(
            Credentials("good.boy@sonarsource.com", "woofwoof"))
    }

    @Test
    fun `should delete xml config with old credentials on write`() {
        getService(CredentialsService::class.java).getDogfoodCredentials()
        StoreUtil.saveSettings(ApplicationManager.getApplication())

        assertThat(PathManager.getOptionsFile("sonarlint-dogfood"))
            .doesNotExist()
    }
}
