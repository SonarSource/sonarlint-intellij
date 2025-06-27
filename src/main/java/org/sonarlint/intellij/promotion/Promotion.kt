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
