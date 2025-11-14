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
package org.sonarlint.intellij.promotion

import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams

private data class UtmParamSet(
    val medium: String = "referral",
    val source: String = "sq-ide-product-intellij",
    val content: String,
    val term: String
) {
    fun toMap() = mapOf(
        "utm_medium" to medium,
        "utm_source" to source,
        "utm_content" to content,
        "utm_term" to term,
    )
}

private const val CREATE_NEW_CONNECTION_PANEL = "create-new-connection-panel"
private const val NOTIFICATION = "notification"

enum class UtmParameters(
    private val content: String,
    private val term: String
) {
    NEW_CONNECTION_PANEL(CREATE_NEW_CONNECTION_PANEL, "explore-sonarqube-cloud-free-tier"),
    DETECT_PROJECT_ISSUES(NOTIFICATION, "detect-project-issues-signup-free"),
    SPEED_UP_ANALYSIS(NOTIFICATION, "speed-up-project-analysis-signup-free"),
    ANALYZE_CI_CD(NOTIFICATION, "analyze-project-ci-cd-pipeline-downloads"),
    DETECT_SECURITY_ISSUES(NOTIFICATION, "detect-security-issues-files-signup-free"),
    ENABLE_LANGUAGE_ANALYSIS(NOTIFICATION, "enable-project-analysis-signup-free"),
    CREATE_SQC_TOKEN(CREATE_NEW_CONNECTION_PANEL, "create-sqc-token");

    private val params = UtmParamSet(content = content, term = term)
    val trackingParams get() = params.toMap()

    fun toDto(): HelpGenerateUserTokenParams.Utm = HelpGenerateUserTokenParams.Utm(
        params.medium, params.source, params.content, params.term
    )
}
