/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.vfs.VirtualFile
import org.sonarsource.sonarlint.core.client.api.connected.RemoteHotspot

data class LocalHotspot(val primaryLocation: Location, val remote: RemoteHotspot) {
    val filePath: String = remote.filePath

    val message: String = remote.message

    val ruleKey: String = remote.rule.key

    val author: String = remote.author

    val statusDescription: String = remote.status.description + if (remote.resolution == null) "" else " as " + remote.resolution.description

    val probability: RemoteHotspot.Rule.Probability = remote.rule.vulnerabilityProbability

    val category: String = remote.rule.securityCategory

    val lineNumber: Int? = remote.textRange.startLine
}

fun unknownLocation(): Location {
    return Location(null, null)
}

fun fileOnlyLocation(file: VirtualFile?): Location {
    return Location(file, null)
}

fun resolvedLocation(file: VirtualFile?, range: RangeMarker?): Location {
    return Location(file, range)
}

data class Location (val file: VirtualFile?, val range: RangeMarker?) {
    val isResolved = file != null && file.isValid && range != null && range.isValid
}
