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
package org.sonarlint.intellij.core.server

import org.sonarlint.intellij.common.util.SonarLintUtils.SONARCLOUD_URL
import org.sonarlint.intellij.common.util.SonarLintUtils.withTrailingSlash

sealed interface ServerLinks {
    fun formattingSyntaxDoc(): String
}

object SonarCloudLinks : ServerLinks {
    override fun formattingSyntaxDoc() = "$SONARCLOUD_URL/markdown/help"
}

class SonarQubeLinks(sonarQubeUrl: String) : ServerLinks {
    override fun formattingSyntaxDoc() = "${baseUrl}formatting/help"

    private val baseUrl: String = withTrailingSlash(sonarQubeUrl)
}
