/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.telemetry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.Settings

class TelemetryClientAttributeProviderImplTests : AbstractSonarLintLightTests() {

    val underTest = TelemetryClientAttributeProviderImpl()

    @Test
    fun defaultDisabledRules() {
        assertThat(underTest.defaultDisabledRules).isEmpty()

        Settings.getGlobalSettings().disableRule("ruleKey1")
        Settings.getGlobalSettings().disableRule("ruleKey1")
        Settings.getGlobalSettings().disableRule("ruleKey2")

        assertThat(underTest.defaultDisabledRules).containsExactly("ruleKey2", "ruleKey1")
    }

    @Test
    fun nonDefaultEnabledRules() {
        assertThat(underTest.nonDefaultEnabledRules).isEmpty()

        Settings.getGlobalSettings().enableRule("ruleKey1")
        Settings.getGlobalSettings().enableRule("ruleKey1")
        Settings.getGlobalSettings().enableRule("ruleKey2")
        Settings.getGlobalSettings().setRuleParam("java:S107", "max", "10")

        assertThat(underTest.nonDefaultEnabledRules).containsExactly("ruleKey2", "ruleKey1")
    }

    @Test
    fun additionalAttribute() {
        assertThat(underTest.additionalAttributes()).containsKey("intellij")
        assertThat(underTest.additionalAttributes().get("intellij") as Map<String, String>).containsKey("jcefSupported")
    }

}
