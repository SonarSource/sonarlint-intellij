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
package org.sonarlint.intellij.ui.currentfile.filter

enum class SeverityFilter(val presentableText: String) {
    NO_FILTER("All"),
    BLOCKER("Blocker"),
    CRITICAL("Critical"),
    MAJOR("Major"),
    MINOR("Minor"),
    INFO("Info");

    override fun toString() = presentableText
}

enum class MqrImpactFilter(val presentableText: String) {
    NO_FILTER("All"),
    BLOCKER("Blocker"),
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
    INFO("Info");

    override fun toString() = presentableText
}

sealed class SeverityImpactFilter {
    data class Severity(val filter: SeverityFilter) : SeverityImpactFilter()
    data class MqrImpact(val filter: MqrImpactFilter) : SeverityImpactFilter()

    fun isNoFilter(): Boolean = when (this) {
        is Severity -> filter == SeverityFilter.NO_FILTER
        is MqrImpact -> filter == MqrImpactFilter.NO_FILTER
    }

    fun getPresentableText(): String = when (this) {
        is Severity -> filter.presentableText
        is MqrImpact -> filter.presentableText
    }
}
