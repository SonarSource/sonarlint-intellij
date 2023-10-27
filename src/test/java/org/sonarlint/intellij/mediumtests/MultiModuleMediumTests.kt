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
package org.sonarlint.intellij.mediumtests

import org.assertj.core.api.Assertions.assertThat
import org.sonarlint.intellij.AbstractSonarLintHeavyTests
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.core.ModuleBindingManager
import org.sonarlint.intellij.fixtures.newSonarQubeConnection

class MultiModuleMediumTests : AbstractSonarLintHeavyTests() {

    fun test_should_return_project_key_for_module_binding_override() {
        val secondModule = createModule("foo")

        connectProjectTo(newSonarQubeConnection("server1"), "project1")
        connectModuleTo(secondModule, "project2")

        assertThat(SonarLintUtils.getService(module, ModuleBindingManager::class.java).resolveProjectKey()).isEqualTo("project1")
        assertThat(SonarLintUtils.getService(secondModule, ModuleBindingManager::class.java).resolveProjectKey()).isEqualTo("project2")
    }

    fun test_should_consider_module_binding_if_only_one_module_but_was_previously_overriden() {
        connectProjectTo(newSonarQubeConnection("server1"), "project1")
        connectModuleTo("overriden")

        assertThat(SonarLintUtils.getService(module, ModuleBindingManager::class.java).resolveProjectKey()).isEqualTo("overriden")
    }
}
