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
package org.sonarlint.intellij.mediumtests

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.PrimaryModuleManager
import com.intellij.openapi.project.Project
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.testFramework.ExtensionTestUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.sonarlint.intellij.AbstractSonarLintHeavyTests
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ModuleBindingManager

class MultiModuleMediumTests : AbstractSonarLintHeavyTests() {

    @Test
    fun `primary project cannot override its binding when projects can be attached`() {
        createModule("attached")
        configureProjectAttachment(true)

        assertThat(SonarLintUtils.getService(module, ModuleBindingManager::class.java).isBindingOverrideAllowed).isFalse()
    }

    @Test
    fun `attached project can override its binding`() {
        val attachedModule = createModule("attached")
        configureProjectAttachment(true)

        assertThat(SonarLintUtils.getService(attachedModule, ModuleBindingManager::class.java).isBindingOverrideAllowed).isTrue()
    }

    @Test
    fun `primary module can override its binding when project attachment is unavailable`() {
        createModule("other")
        configureProjectAttachment(false)

        assertThat(SonarLintUtils.getService(module, ModuleBindingManager::class.java).isBindingOverrideAllowed).isTrue()
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun test_should_return_project_key_for_module_binding_override() {
        val secondModule = createModule("foo")

        connectProjectTo(ServerConnection.newBuilder().setName("server1").build(), "project1")
        connectModuleTo(secondModule, "project2")

        assertThat(SonarLintUtils.getService(module, ModuleBindingManager::class.java).resolveProjectKey()).isEqualTo("project1")
        assertThat(SonarLintUtils.getService(secondModule, ModuleBindingManager::class.java).resolveProjectKey()).isEqualTo("project2")
    }

    @Test
    fun test_should_consider_module_binding_if_only_one_module_but_was_previously_overriden() {
        connectProjectTo(ServerConnection.newBuilder().setName("server1").build(), "project1")
        connectModuleTo("overriden")

        assertThat(SonarLintUtils.getService(module, ModuleBindingManager::class.java).resolveProjectKey()).isEqualTo("overriden")
    }

    private fun configureProjectAttachment(available: Boolean) {
        ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME,
            if (available) listOf(ProjectAttachProcessor()) else emptyList(), testRootDisposable)
        ExtensionTestUtil.maskExtensions(PrimaryModuleManager.EP_NAME, listOf(object : PrimaryModuleManager() {
            override fun getPrimaryModule(project: Project): Module = module
        }), testRootDisposable)
    }

}
