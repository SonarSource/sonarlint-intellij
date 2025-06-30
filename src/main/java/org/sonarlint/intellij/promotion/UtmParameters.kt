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
package org.sonarlint.intellij.promotion

import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenParams

private const val MEDIUM = "utm_medium"
private const val SOURCE = "utm_source"
private const val CONTENT = "utm_content"
private const val TERM = "utm_term"

private val BASE_PARAMETERS = mapOf(
    MEDIUM to "referral",
    SOURCE to "sq-ide-product-intellij",
)
private val NOTIFICATION_PARAMETERS = BASE_PARAMETERS +
    (CONTENT to "notification")

/**
 * Sets of parameters used by Google Analytics to track where the clicks are coming from.
 * Each enum value corresponds to a place where a link can be clicked so that it has its own set of passed parameters.
 */
enum class UtmParameters(val trackingParams: Map<String, String>) {

    NEW_CONNECTION_PANEL(
        BASE_PARAMETERS + mapOf(
            CONTENT to "create-new-connection-panel",
            TERM to "explore-sonarqube-cloud-free-tier",
        )
    ),
    DETECT_PROJECT_ISSUES(
        NOTIFICATION_PARAMETERS +
            (TERM to "detect-project-issues-signup-free")
    ),
    SPEED_UP_ANALYSIS(
        NOTIFICATION_PARAMETERS +
            (TERM to "speed-up-project-analysis-signup-free")
    ),
    ANALYZE_CI_CD(
        NOTIFICATION_PARAMETERS +
            (TERM to "analyze-project-ci-cd-pipeline-downloads")
    ),
    DETECT_SECURITY_ISSUES(
        NOTIFICATION_PARAMETERS +
            (TERM to "detect-security-issues-files-signup-free")
    ),
    ENABLE_LANGUAGE_ANALYSIS(
        NOTIFICATION_PARAMETERS +
            (TERM to "enable-project-analysis-signup-free")
    );

    fun toDto(): HelpGenerateUserTokenParams.Utm {
        return HelpGenerateUserTokenParams.Utm(
            trackingParams[MEDIUM],
            trackingParams[SOURCE],
            trackingParams[CONTENT],
            trackingParams[TERM]
        )
    }
}
