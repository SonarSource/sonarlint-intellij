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
package org.sonarlint.intellij.telemetry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.promotion.UtmParameters

class LinkTelemetryTests {

    @Test
    fun should_build_url_with_utm_parameters_for_new_connection_panel() {
        val url = LinkTelemetry.SONARCLOUD_FREE_SIGNUP_PAGE.withParameters(UtmParameters.NEW_CONNECTION_PANEL)

        assertThat(url).contains("utm_medium=referral")
            .contains("utm_source=sq-ide-product-intellij")
            .contains("utm_content=create-new-connection-panel")
            .contains("utm_term=explore-sonarqube-cloud-free-tier")
            .startsWith("https://www.sonarsource.com/products/sonarcloud/")
    }

    @Test
    fun should_build_url_with_utm_parameters_for_detect_project_issues() {
        val url = LinkTelemetry.SONARCLOUD_FREE_SIGNUP_PAGE.withParameters(UtmParameters.DETECT_PROJECT_ISSUES)

        assertThat(url).contains("utm_medium=referral")
            .contains("utm_source=sq-ide-product-intellij")
            .contains("utm_content=notification")
            .contains("utm_term=detect-project-issues-signup-free")
            .startsWith("https://www.sonarsource.com/products/sonarcloud/")
    }

    @Test
    fun should_build_url_with_utm_parameters_for_speed_up_analysis() {
        val url = LinkTelemetry.SONARCLOUD_FREE_SIGNUP_PAGE.withParameters(UtmParameters.SPEED_UP_ANALYSIS)

        assertThat(url).contains("utm_medium=referral")
            .contains("utm_source=sq-ide-product-intellij")
            .contains("utm_content=notification")
            .contains("utm_term=speed-up-project-analysis-signup-free")
            .startsWith("https://www.sonarsource.com/products/sonarcloud/")
    }

    @Test
    fun should_build_url_with_utm_parameters_for_analyze_ci_cd() {
        val url = LinkTelemetry.SONARQUBE_EDITIONS_DOWNLOADS.withParameters(UtmParameters.ANALYZE_CI_CD)

        assertThat(url).contains("utm_medium=referral")
            .contains("utm_source=sq-ide-product-intellij")
            .contains("utm_content=notification")
            .contains("utm_term=analyze-project-ci-cd-pipeline-downloads")
            .startsWith("https://www.sonarsource.com/products/sonarqube/")
    }

    @Test
    fun should_build_url_with_utm_parameters_for_detect_security_issues() {
        val url = LinkTelemetry.SONARCLOUD_FREE_SIGNUP_PAGE.withParameters(UtmParameters.DETECT_SECURITY_ISSUES)

        assertThat(url).contains("utm_medium=referral")
            .contains("utm_source=sq-ide-product-intellij")
            .contains("utm_content=notification")
            .contains("utm_term=detect-security-issues-files-signup-free")
            .startsWith("https://www.sonarsource.com/products/sonarcloud/")
    }

    @Test
    fun should_build_url_with_utm_parameters_for_enable_language_analysis() {
        val url = LinkTelemetry.SONARCLOUD_FREE_SIGNUP_PAGE.withParameters(UtmParameters.ENABLE_LANGUAGE_ANALYSIS)

        assertThat(url).contains("utm_medium=referral")
            .contains("utm_source=sq-ide-product-intellij")
            .contains("utm_content=notification")
            .contains("utm_term=enable-project-analysis-signup-free")
            .startsWith("https://www.sonarsource.com/products/sonarcloud/")
    }

    @Test
    fun should_build_url_with_utm_parameters_for_create_sqc_token() {
        val url = LinkTelemetry.SONARCLOUD_FREE_SIGNUP_PAGE.withParameters(UtmParameters.CREATE_SQC_TOKEN)

        assertThat(url).contains("utm_medium=referral")
            .contains("utm_source=sq-ide-product-intellij")
            .contains("utm_content=create-new-connection-panel")
            .contains("utm_term=create-sqc-token")
            .startsWith("https://www.sonarsource.com/products/sonarcloud/")
    }

    @Test
    fun should_build_url_without_utm_parameters_when_null() {
        val url = LinkTelemetry.CONNECTED_MODE_DOCS.withParameters(null)

        assertThat(url).doesNotContain("utm_")
            .isEqualTo(LinkTelemetry.CONNECTED_MODE_DOCS.url)
    }

    @Test
    fun should_build_url_with_correct_parameter_separators() {
        val url = LinkTelemetry.SONARCLOUD_FREE_SIGNUP_PAGE.withParameters(UtmParameters.NEW_CONNECTION_PANEL)

        // Should start with ? for the first parameter and use & for later ones
        assertThat(url).contains("?utm_medium=referral")
            .contains("&utm_source=sq-ide-product-intellij")
            .contains("&utm_content=create-new-connection-panel")
            .contains("&utm_term=explore-sonarqube-cloud-free-tier")
    }

    @Test
    fun should_have_valid_urls_for_all_link_telemetry_entries() {
        val validUrlPatterns = listOf(
            "https://www.sonarsource.com/",
            "https://docs.sonarsource.com/",
            "https://community.sonarsource.com/"
        )

        for (linkTelemetry in LinkTelemetry.values()) {
            val url = linkTelemetry.url
            assertThat(url).isNotBlank()
            assertThat(validUrlPatterns.any { url.startsWith(it) }).isTrue()
        }
    }

    @Test
    fun should_build_complex_url_with_all_utm_parameters() {
        val url = LinkTelemetry.SONARCLOUD_FREE_SIGNUP_PAGE.withParameters(UtmParameters.CREATE_SQC_TOKEN)

        assertThat(url).matches(
            "https://www\\.sonarsource\\.com/products/sonarcloud/.*" +
                "\\?utm_medium=referral" +
                "&utm_source=sq-ide-product-intellij" +
                "&utm_content=create-new-connection-panel" +
                "&utm_term=create-sqc-token"
        )
    }

    @Test
    fun should_handle_urls_with_existing_parameters() {
        // Test that the UTM parameters are added correctly even if the base URL might have parameters
        val url = LinkTelemetry.SONARQUBE_EDITIONS_DOWNLOADS.withParameters(UtmParameters.ANALYZE_CI_CD)

        assertThat(url).contains("utm_medium=referral")
            .contains("utm_source=sq-ide-product-intellij")
            .contains("utm_content=notification")
            .contains("utm_term=analyze-project-ci-cd-pipeline-downloads")
    }

    @Test
    fun should_build_url_with_utm_parameters_for_all_link_types() {
        // Test that all link types can handle UTM parameters correctly
        val testUtmParams = UtmParameters.NEW_CONNECTION_PANEL

        for (linkTelemetry in LinkTelemetry.values()) {
            val url = linkTelemetry.withParameters(testUtmParams)

            // Should contain all UTM parameters
            assertThat(url).contains("utm_medium=referral")
                .contains("utm_source=sq-ide-product-intellij")
                .contains("utm_content=create-new-connection-panel")
                .contains("utm_term=explore-sonarqube-cloud-free-tier")

                // Should start with the original URL
                .startsWith(linkTelemetry.url)
        }
    }
} 
