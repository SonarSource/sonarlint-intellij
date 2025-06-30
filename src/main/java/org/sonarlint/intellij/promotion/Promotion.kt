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

private val BASE_PARAMETERS = mapOf(
    "utm_medium" to "referral",
    "utm_source" to "sq-ide-product-intellij"
)
private val NOTIFICATION_PARAMETERS = BASE_PARAMETERS +
    ("utm_content" to "notification")

enum class Promotion(val trackingParams: Map<String, String>) {

    NEW_CONNECTION_PANEL(
        BASE_PARAMETERS + mapOf(
            "utm_content" to "create-new-connection-panel",
            "utm_term" to "explore-sonarqube-cloud-free-tier"
        )
    ),
    DETECT_PROJECT_ISSUES(
        NOTIFICATION_PARAMETERS +
            ("utm_term" to "detect-project-issues-signup-free")
    ),
    SPEED_UP_ANALYSIS(
        NOTIFICATION_PARAMETERS +
            ("utm_term" to "speed-up-project-analysis-signup-free")
    ),
    ANALYZE_CI_CD(
        NOTIFICATION_PARAMETERS +
            ("utm_term" to "analyze-project-ci-cd-pipeline-downloads")
    ),
    DETECT_SECURITY_ISSUES(
        NOTIFICATION_PARAMETERS +
            ("utm_term" to "enable-project-analysis-signup-free")
    ),
    ENABLE_LANGUAGE_ANALYSIS(
        NOTIFICATION_PARAMETERS +
            ("utm_term" to "enable-project-analysis-signup-free")
    );
}
