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
package org.sonarlint.intellij.documentation

object SonarLintDocumentation {

    object Intellij {
        const val BASE_DOCS_URL = "https://docs.sonarsource.com/sonarlint/intellij"
        const val CONNECTED_MODE_LINK = "${BASE_DOCS_URL}/team-features/connected-mode"
        const val CONNECTED_MODE_SETUP_LINK = "${CONNECTED_MODE_LINK}/#connection-setup"
        const val SECURITY_HOTSPOTS_LINK = "${BASE_DOCS_URL}/using-sonarlint/security-hotspots"
        const val TAINT_VULNERABILITIES_LINK = "${BASE_DOCS_URL}/using-sonarlint/taint-vulnerabilities"
        const val CLEAN_CODE_LINK = "${BASE_DOCS_URL}/concepts/clean-code/introduction"
        const val SUPPORT_POLICY_LINK = "${BASE_DOCS_URL}/team-features/connected-mode/#sonarlint-sonarqube-version-support-policy"
        const val FOCUS_ON_NEW_CODE_LINK = "${BASE_DOCS_URL}/using-sonarlint/investigating-issues/#focusing-on-new-code"
        const val CONNECTED_MODE_BENEFITS_LINK = "${BASE_DOCS_URL}/team-features/connected-mode/#benefits"
        const val TROUBLESHOOTING_CONNECTED_MODE_SETUP_LINK = "${BASE_DOCS_URL}/troubleshooting/#troubleshooting-connected-mode-setup"
    }

    object Marketing {
        private const val BASE_MARKETING_URL = "https://www.sonarsource.com"
        const val COMPARE_SERVER_PRODUCTS_LINK = "${BASE_MARKETING_URL}/open-source-editions"
        const val SONARQUBE_EDITIONS_DOWNLOADS_LINK = "${BASE_MARKETING_URL}/products/sonarqube/downloads"
        const val SONARCLOUD_PRODUCT_LINK = "${BASE_MARKETING_URL}/products/sonarcloud"
        const val SONARCLOUD_PRODUCT_SIGNUP_LINK = "${BASE_MARKETING_URL}/products/sonarcloud/signup/"
    }

}
