package org.sonarlint.intellij.ui.traffic.light

data class SonarLintDashboardModel(val issuesCount: Int, val hotspotsCount: Int, val taintVulnerabilitiesCount: Int, val isFocusOnNewCode: Boolean) {
    fun findingsCount() = issuesCount + hotspotsCount + taintVulnerabilitiesCount
    fun hasFindings() = findingsCount() != 0
}