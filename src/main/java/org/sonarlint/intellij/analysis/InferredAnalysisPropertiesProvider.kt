/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.analysis

import com.intellij.openapi.module.Module
import java.net.URI
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.util.VirtualFileUtils

object InferredAnalysisPropertiesProvider {

    fun getConfigurationFromConfiguratorEP(
        module: Module, filesToAnalyze: List<URI>,
        console: SonarLintConsole
    ): List<AnalysisConfigurator.AnalysisConfiguration> {
        val files = filesToAnalyze.map { VirtualFileUtils.uriToVirtualFile(it) }
        return AnalysisConfigurator.EP_NAME.extensionList
            .map { config ->
                console.debug("Configuring analysis with " + config.javaClass.getName())
                config.configure(module, files)
            }
    }

    fun collectContributedExtraProperties(
        module: Module, console: SonarLintConsole,
        contributedConfigurations: List<AnalysisConfigurator.AnalysisConfiguration>
    ): Map<String, String> {
        val contributedProperties = HashMap<String, String>()
        contributedConfigurations.forEach { config ->
            config.extraProperties.entries.forEach { entry ->
                if (contributedProperties.containsKey(entry.key) && contributedProperties[entry.key] != entry.value) {
                    console.error(
                        "The same property ${entry.key} was contributed by multiple configurators with different values: " +
                            "${contributedProperties[entry.key]} / ${entry.value}"
                    )
                }
                contributedProperties[entry.key] = entry.value
            }
        }

        Settings.getSettingsFor(module.project).additionalProperties.entries.forEach { entry ->
            if (contributedProperties.containsKey(entry.key) && contributedProperties[entry.key] != entry.value) {
                console.error(
                    "The same property ${entry.key} was contributed by multiple configurators with different values: " +
                        "${contributedProperties[entry.key]} / ${entry.value}"
                )
            }
            contributedProperties[entry.key] = entry.value
        }
        return contributedProperties
    }

}
