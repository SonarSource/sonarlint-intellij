package org.sonarlint.intellij.finding

enum class FindingType(private val displayName: String, private val displayNamePlural: String = "${displayName}s") {
    ISSUE("issue"), SECURITY_HOTSPOT("security hotspot"), TAINT_VULNERABILITY("taint vulnerability", "taint vulnerabilities");

    fun display(findingCount: Int) : String {
        return when (findingCount) {
            0 -> "No $displayNamePlural"
            1 -> "1 $displayName"
            else -> "$findingCount $displayNamePlural"
        }
    }
}