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

import java.net.HttpURLConnection
import java.net.URL
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled("Only for manual testing - this test should not impact realisability")
class SonarLintDocumentationTests {

    @ParameterizedTest
    @MethodSource("pagesProvider")
    fun should_verify_page_is_not_404(pageUrl: String) {
        val url = URL(pageUrl)

        val connection = url.openConnection() as HttpURLConnection

        val responseCode = connection.responseCode
        assertThat(responseCode).isNotEqualTo(404)
    }

    private fun pagesProvider() = listOf(
        SonarLintDocumentation.Intellij.BASE_DOCS_URL,
        SonarLintDocumentation.Intellij.CONNECTED_MODE_LINK,
        SonarLintDocumentation.Intellij.CONNECTED_MODE_SETUP_LINK,
        SonarLintDocumentation.Intellij.SECURITY_HOTSPOTS_LINK,
        SonarLintDocumentation.Intellij.TAINT_VULNERABILITIES_LINK,
        SonarLintDocumentation.Intellij.CLEAN_CODE_LINK,
        SonarLintDocumentation.Intellij.SUPPORT_POLICY_LINK,
        SonarLintDocumentation.Intellij.FOCUS_ON_NEW_CODE_LINK,
        SonarLintDocumentation.Intellij.CONNECTED_MODE_BENEFITS_LINK,
        SonarLintDocumentation.Intellij.SHARING_CONNECTED_MODE_CONFIGURATION_LINK,
        SonarLintDocumentation.Intellij.TROUBLESHOOTING_CONNECTED_MODE_SETUP_LINK,
        SonarLintDocumentation.Intellij.RULE_SECTION_LINK,
        SonarLintDocumentation.Intellij.FILE_EXCLUSION_LINK,
        SonarLintDocumentation.Intellij.AI_FIX_SUGGESTIONS_LINK,
        SonarLintDocumentation.Intellij.INVESTIGATING_ISSUES_LINK,
        SonarLintDocumentation.Intellij.OPEN_IN_IDE_LINK,
        SonarLintDocumentation.Intellij.AI_CAPABILITIES,

        SonarLintDocumentation.SonarQube.SMART_NOTIFICATIONS,

        SonarLintDocumentation.SonarCloud.SMART_NOTIFICATIONS,

        SonarLintDocumentation.Marketing.SONARQUBE_EDITIONS_DOWNLOADS_LINK,
        SonarLintDocumentation.Marketing.SONARCLOUD_PRODUCT_LINK,
        SonarLintDocumentation.Marketing.SONARCLOUD_PRODUCT_SIGNUP_LINK,
        SonarLintDocumentation.Marketing.SONARQUBE_FOR_IDE_ROADMAP_LINK
    )

}
