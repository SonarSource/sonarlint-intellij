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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UtmParametersTest {

    @Test
    fun should_transfer_fields_to_dto() {
        val dto = UtmParameters.CREATE_SQC_TOKEN.toDto()

        assertThat(dto).extracting(
            "medium",
            "source",
            "content",
            "term",
        ).containsExactly(
            "referral",
            "sq-ide-product-intellij",
            "create-new-connection-panel",
            "create-sqc-token",
        )
    }

    @Test
    fun should_have_correct_tracking_params_for_new_connection_panel() {
        val trackingParams = UtmParameters.NEW_CONNECTION_PANEL.trackingParams

        assertThat(trackingParams).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "utm_medium" to "referral",
                "utm_source" to "sq-ide-product-intellij",
                "utm_content" to "create-new-connection-panel",
                "utm_term" to "explore-sonarqube-cloud-free-tier"
            )
        )
    }

    @Test
    fun should_have_correct_tracking_params_for_detect_project_issues() {
        val trackingParams = UtmParameters.DETECT_PROJECT_ISSUES.trackingParams

        assertThat(trackingParams).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "utm_medium" to "referral",
                "utm_source" to "sq-ide-product-intellij",
                "utm_content" to "notification",
                "utm_term" to "detect-project-issues-signup-free"
            )
        )
    }

    @Test
    fun should_have_correct_tracking_params_for_speed_up_analysis() {
        val trackingParams = UtmParameters.SPEED_UP_ANALYSIS.trackingParams

        assertThat(trackingParams).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "utm_medium" to "referral",
                "utm_source" to "sq-ide-product-intellij",
                "utm_content" to "notification",
                "utm_term" to "speed-up-project-analysis-signup-free"
            )
        )
    }

    @Test
    fun should_have_correct_tracking_params_for_analyze_ci_cd() {
        val trackingParams = UtmParameters.ANALYZE_CI_CD.trackingParams

        assertThat(trackingParams).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "utm_medium" to "referral",
                "utm_source" to "sq-ide-product-intellij",
                "utm_content" to "notification",
                "utm_term" to "analyze-project-ci-cd-pipeline-downloads"
            )
        )
    }

    @Test
    fun should_have_correct_tracking_params_for_detect_security_issues() {
        val trackingParams = UtmParameters.DETECT_SECURITY_ISSUES.trackingParams

        assertThat(trackingParams).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "utm_medium" to "referral",
                "utm_source" to "sq-ide-product-intellij",
                "utm_content" to "notification",
                "utm_term" to "detect-security-issues-files-signup-free"
            )
        )
    }

    @Test
    fun should_have_correct_tracking_params_for_enable_language_analysis() {
        val trackingParams = UtmParameters.ENABLE_LANGUAGE_ANALYSIS.trackingParams

        assertThat(trackingParams).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "utm_medium" to "referral",
                "utm_source" to "sq-ide-product-intellij",
                "utm_content" to "notification",
                "utm_term" to "enable-project-analysis-signup-free"
            )
        )
    }

    @Test
    fun should_have_correct_tracking_params_for_create_sqc_token() {
        val trackingParams = UtmParameters.CREATE_SQC_TOKEN.trackingParams

        assertThat(trackingParams).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "utm_medium" to "referral",
                "utm_source" to "sq-ide-product-intellij",
                "utm_content" to "create-new-connection-panel",
                "utm_term" to "create-sqc-token"
            )
        )
    }

    @Test
    fun should_have_consistent_medium_and_source_for_all_parameters() {
        val allParameters = UtmParameters.values()

        for (param in allParameters) {
            val trackingParams = param.trackingParams
            assertThat(trackingParams["utm_medium"]).isEqualTo("referral")
            assertThat(trackingParams["utm_source"]).isEqualTo("sq-ide-product-intellij")
        }
    }

    @Test
    fun should_have_unique_terms_for_all_parameters() {
        val allParameters = UtmParameters.values()
        val terms = allParameters.map { it.trackingParams["utm_term"] }

        assertThat(terms).doesNotHaveDuplicates()
    }

    @Test
    fun should_have_valid_content_values() {
        val allParameters = UtmParameters.values()
        val validContents = setOf("create-new-connection-panel", "notification")

        for (param in allParameters) {
            val content = param.trackingParams["utm_content"]
            assertThat(content).isIn(validContents)
        }
    }

}
