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
package org.sonarlint.intellij.documentation

object SonarLintDocumentation {

    object Intellij {
        const val BASE_DOCS_URL = "https://docs.sonarsource.com/sonarqube-for-ide/intellij"
        const val CONNECTED_MODE_LINK = "$BASE_DOCS_URL/team-features/connected-mode"
        const val CONNECTED_MODE_SETUP_LINK = "$BASE_DOCS_URL/team-features/connected-mode-setup"
        const val SECURITY_HOTSPOTS_LINK = "$BASE_DOCS_URL/using/security-hotspots"
        const val TAINT_VULNERABILITIES_LINK = "$BASE_DOCS_URL/using/taint-vulnerabilities"
        const val CLEAN_CODE_LINK = "$BASE_DOCS_URL/using/software-qualities/"
        const val SUPPORT_POLICY_LINK = "$CONNECTED_MODE_SETUP_LINK/#sonarlint-sonarqube-version-support-policy"
        const val FOCUS_ON_NEW_CODE_LINK = "$BASE_DOCS_URL/using/investigating-issues/#focusing-on-new-code"
        const val CONNECTED_MODE_BENEFITS_LINK = "$CONNECTED_MODE_LINK/#benefits"
        const val SHARING_CONNECTED_MODE_CONFIGURATION_LINK = "$CONNECTED_MODE_SETUP_LINK/#reuse-the-binding-configuration"
        const val TROUBLESHOOTING_CONNECTED_MODE_SETUP_LINK = "$BASE_DOCS_URL/troubleshooting/#troubleshooting-connected-mode-setup"
        const val TROUBLESHOOTING_LINK = "$BASE_DOCS_URL/troubleshooting"
        const val RULE_SECTION_LINK = "$BASE_DOCS_URL/using/rules/#rule-selection"
        const val USING_RULES_LINK = "$BASE_DOCS_URL/using/rules"
        const val FILE_EXCLUSION_LINK = "$BASE_DOCS_URL/using/file-exclusions"
        const val AI_FIX_SUGGESTIONS_LINK = "$BASE_DOCS_URL/using/investigating-issues/#ai-generated-fix-suggestions"
        const val INVESTIGATING_ISSUES_LINK = "$BASE_DOCS_URL/using/investigating-issues"
        const val OPEN_IN_IDE_LINK = "$BASE_DOCS_URL/using/investigating-issues/#opening-issues-in-the-ide"
        const val AI_CAPABILITIES = "$BASE_DOCS_URL/ai-capabilities/ai-codefix"
    }

    object Community {
        const val COMMUNITY_LINK = "https://community.sonarsource.com/c/sl/fault/6"
    }

    object SonarQube {
        const val SMART_NOTIFICATIONS = "https://docs.sonarsource.com/sonarqube-server/latest/user-guide/connected-mode/#smart-notifications"
    }

    object SonarCloud {
        const val SMART_NOTIFICATIONS = "https://docs.sonarsource.com/sonarqube-cloud/improving/connected-mode/#smart-notifications"
    }

    object Marketing {
        private const val BASE_MARKETING_URL = "https://www.sonarsource.com"
        const val SONARQUBE_EDITIONS_DOWNLOADS_LINK = "$BASE_MARKETING_URL/products/sonarqube/downloads"
        const val SONARCLOUD_PRODUCT_LINK = "$BASE_MARKETING_URL/products/sonarcloud"
        const val SONARCLOUD_PRODUCT_SIGNUP_LINK = "$BASE_MARKETING_URL/products/sonarcloud/signup-free/"
        const val SONARQUBE_FOR_IDE_ROADMAP_LINK = "$BASE_MARKETING_URL/products/sonarlint/roadmap/"
    }

}
