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
package org.sonarlint.intellij.config.project

import org.sonarsource.sonarlint.core.rpc.protocol.common.Language

enum class AnalyzerStatus(val label: String, val tooltip: String? = null) {
    ACTIVE("Active"),
    SYNCED("Synced"),
    DOWNLOADING("Downloading\u2026"),
    FAILED("Failed"),
    PREMIUM("Premium", "Requires Connected Mode"),
    UNSUPPORTED("Unsupported"),
}

enum class AnalyzerSource(val label: String) {
    LOCAL("Local"),
    SONARQUBE_SERVER("SQS"),
    SONARQUBE_CLOUD("SQC"),
}

data class SupportedLanguageRow(
    val language: Language,
    val displayName: String,
    val status: AnalyzerStatus,
    val source: AnalyzerSource,
    /** Version currently active (embedded or synced from server). */
    val version: String?,
    /**
     * The locally embedded version of the analyzer, used to detect when the server is
     * overriding the local version. `null` when there is no local version (e.g. Premium
     * languages not yet downloaded) or when the concept does not apply.
     */
    val localVersion: String? = null,
) {
    /** True when the server is providing a different version than the locally embedded one. */
    val isVersionOverriddenByServer: Boolean
        get() = localVersion != null && version != null && version != localVersion
}
