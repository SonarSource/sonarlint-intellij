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
package org.sonarlint.intellij.telemetry

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.jcef.JBCefApp
import java.util.Optional
import java.util.function.Predicate
import java.util.stream.Collectors
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.ServerConnectionService
import org.sonarlint.intellij.config.global.SonarCloudConnection
import org.sonarlint.intellij.core.EngineManager
import org.sonarlint.intellij.core.NodeJsManager
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails
import org.sonarsource.sonarlint.core.telemetry.TelemetryClientAttributesProvider

class TelemetryClientAttributeProviderImpl : TelemetryClientAttributesProvider {

    override fun usesConnectedMode(): Boolean {
        return isAnyProjectConnected()
    }

    override fun useSonarCloud(): Boolean {
        return isAnyProjectConnectedToSonarCloud()
    }

    override fun nodeVersion(): Optional<String> {
        val nodeJsManager = SonarLintUtils.getService(NodeJsManager::class.java)
        return Optional.ofNullable(nodeJsManager.nodeJsVersion?.toString())
    }

    override fun devNotificationsDisabled(): Boolean {
        return isDevNotificationsDisabled()
    }

    override fun getNonDefaultEnabledRules(): Collection<String> {
        val rules = Settings.getGlobalSettings().rulesByKey
            .filterValues { it.isActive }
            .keys
        val defaultEnabledRuleKeys = defaultEnabledRuleKeys()
        return rules.minus(defaultEnabledRuleKeys)
    }

    override fun getDefaultDisabledRules(): Collection<String> {
        return Settings.getGlobalSettings().rulesByKey
            .filterValues { !it.isActive }
            .keys
    }

    override fun additionalAttributes(): Map<String, Any> {
        return mapOf("intellij" to mapOf("jcefSupported" to JBCefApp.isSupported()))
    }

    private fun defaultEnabledRuleKeys(): Set<String> {
        val engineManager = SonarLintUtils.getService(EngineManager::class.java)
        return engineManager.standaloneEngine.allRuleDetails.stream()
            .filter { obj: StandaloneRuleDetails -> obj.isActiveByDefault }
            .map { obj: StandaloneRuleDetails -> obj.key }
            .collect(Collectors.toSet())
    }

    companion object {

        private fun isAnyProjectConnected(): Boolean =
            isAnyOpenProjectMatch { p: Project -> Settings.getSettingsFor(p).isBindingEnabled }

        private fun isAnyProjectConnectedToSonarCloud(): Boolean = isAnyOpenProjectMatch { p: Project ->
            val bindingManager = SonarLintUtils.getService(p, ProjectBindingManager::class.java)
            bindingManager.tryGetServerConnection()
                .filter { it is SonarCloudConnection }
                .isPresent
        }

        private fun isDevNotificationsDisabled(): Boolean = ServerConnectionService.getInstance().getConnections()
            .any { it.notificationsDisabled }

        private fun isAnyOpenProjectMatch(predicate: Predicate<Project>): Boolean {
            return ProjectManager.getInstance().openProjects.any { predicate.test(it) }
        }

    }
}
