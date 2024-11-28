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

        SonarLintDocumentation.SonarQube.SMART_NOTIFICATIONS,

        SonarLintDocumentation.SonarCloud.SMART_NOTIFICATIONS,

        SonarLintDocumentation.Marketing.COMPARE_SERVER_PRODUCTS_LINK,
        SonarLintDocumentation.Marketing.SONARQUBE_EDITIONS_DOWNLOADS_LINK,
        SonarLintDocumentation.Marketing.SONARCLOUD_PRODUCT_LINK,
        SonarLintDocumentation.Marketing.SONARCLOUD_PRODUCT_SIGNUP_LINK,
    )

}
