/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij.issue.hotspot

import org.sonarlint.intellij.issue.Location
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspotDetails

data class LocalHotspot(val primaryLocation: Location, private val serverHotspot: ServerHotspotDetails) {
    val filePath: String = serverHotspot.filePath

    val message: String = serverHotspot.message

    val ruleKey: String = serverHotspot.rule.key

    val author: String = serverHotspot.author

    val resolution = serverHotspot.resolution
    val statusDescription: String = serverHotspot.status.description + if (resolution == null) "" else " as " + resolution.description

    val probability: VulnerabilityProbability = serverHotspot.rule.vulnerabilityProbability

    val category: String = serverHotspot.rule.securityCategory

    val lineNumber: Int? = serverHotspot.textRange.startLine

    val rule: ServerHotspotDetails.Rule = serverHotspot.rule
}
