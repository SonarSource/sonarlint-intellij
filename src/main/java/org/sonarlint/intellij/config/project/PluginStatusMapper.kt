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
package org.sonarlint.intellij.config.project

import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.ArtifactSourceDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language

/**
 * Maps backend [PluginStatusDto] instances to UI [SupportedLanguageRow] instances.
 *
 * The [DISPLAY_NAME_TO_LANGUAGE] map uses display names produced by the backend
 * (sourced from `SonarLanguage.getName()`) as keys to resolve the corresponding
 * [Language] enum values for icon lookup on the client side.
 */
object PluginStatusMapper {

    private val DISPLAY_NAME_TO_LANGUAGE: Map<String, Language> = mapOf(
        "Abap" to Language.ABAP,
        "Apex" to Language.APEX,
        "C" to Language.C,
        "C++" to Language.CPP,
        "C#" to Language.CS,
        "CSS" to Language.CSS,
        "Objective-C" to Language.OBJC,
        "COBOL" to Language.COBOL,
        "HTML" to Language.HTML,
        "IPython Notebook" to Language.IPYTHON,
        "Java" to Language.JAVA,
        "JCL" to Language.JCL,
        "JavaScript" to Language.JS,
        "Kotlin" to Language.KOTLIN,
        "PHP" to Language.PHP,
        "PL/I" to Language.PLI,
        "PL/SQL" to Language.PLSQL,
        "Python" to Language.PYTHON,
        "RPG" to Language.RPG,
        "Ruby" to Language.RUBY,
        "Scala" to Language.SCALA,
        "Secrets" to Language.SECRETS,
        "Text" to Language.TEXT,
        "Swift" to Language.SWIFT,
        "T-SQL" to Language.TSQL,
        "TypeScript" to Language.TS,
        "JSP" to Language.JSP,
        "VB.NET" to Language.VBNET,
        "XML" to Language.XML,
        "YAML" to Language.YAML,
        "JSON" to Language.JSON,
        "Go" to Language.GO,
        "CloudFormation" to Language.CLOUDFORMATION,
        "Docker" to Language.DOCKER,
        "Kubernetes" to Language.KUBERNETES,
        "Terraform" to Language.TERRAFORM,
        "Azure Resource Manager" to Language.AZURERESOURCEMANAGER,
        "Ansible" to Language.ANSIBLE,
        "GitHub Actions" to Language.GITHUBACTIONS,
    )

    fun mapToRows(pluginStatuses: List<PluginStatusDto>): List<SupportedLanguageRow> {
        return pluginStatuses.mapNotNull { dto -> mapToRow(dto) }
            .filter { it.status != AnalyzerStatus.UNSUPPORTED && it.status != AnalyzerStatus.PREMIUM }
    }

    private fun mapToRow(dto: PluginStatusDto): SupportedLanguageRow? {
        val language = DISPLAY_NAME_TO_LANGUAGE[dto.pluginName] ?: return null
        return SupportedLanguageRow(
            language = language,
            displayName = dto.pluginName,
            status = mapState(dto.state),
            source = mapSource(dto.source),
            version = dto.actualVersion,
            localVersion = dto.overriddenVersion,
        )
    }

    private fun mapState(state: PluginStateDto): AnalyzerStatus = when (state) {
        PluginStateDto.ACTIVE -> AnalyzerStatus.ACTIVE
        PluginStateDto.SYNCED -> AnalyzerStatus.SYNCED
        PluginStateDto.DOWNLOADING -> AnalyzerStatus.DOWNLOADING
        PluginStateDto.FAILED -> AnalyzerStatus.FAILED
        PluginStateDto.PREMIUM -> AnalyzerStatus.PREMIUM
        PluginStateDto.UNSUPPORTED -> AnalyzerStatus.UNSUPPORTED
    }

    private fun mapSource(source: ArtifactSourceDto?): AnalyzerSource = when (source) {
        ArtifactSourceDto.SONARQUBE_SERVER -> AnalyzerSource.SONARQUBE_SERVER
        ArtifactSourceDto.SONARQUBE_CLOUD -> AnalyzerSource.SONARQUBE_CLOUD
        ArtifactSourceDto.EMBEDDED, ArtifactSourceDto.ON_DEMAND, null -> AnalyzerSource.LOCAL
    }

}
