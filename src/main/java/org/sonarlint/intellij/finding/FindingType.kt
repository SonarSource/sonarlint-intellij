/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

    fun displayLabel(findingCount: Int) : String {
        return when (findingCount) {
            0 -> displayNamePlural
            1 -> displayName
            else -> displayNamePlural
        }
    }
}
