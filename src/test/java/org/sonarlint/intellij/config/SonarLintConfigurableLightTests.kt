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
package org.sonarlint.intellij.config

import com.intellij.openapi.options.Configurable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable

class SonarLintConfigurableLightTests : AbstractSonarLintLightTests() {

    @Test
    fun should_resolve_global_settings_from_application_configurable_ep() {
        val configurable = Configurable.APPLICATION_CONFIGURABLE.extensionList
            .single { it.id == "sonarlint" }
            .createConfigurable() as SonarLintGlobalConfigurable

        assertThat(configurable.displayName).isEqualTo("SonarQube for IDE")
        assertThat(configurable.createComponent()).isNotNull()
        configurable.disposeUIResources()
    }
}
