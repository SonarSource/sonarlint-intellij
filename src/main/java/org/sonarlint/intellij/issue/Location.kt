/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.issue

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

fun unknownLocation(message: String?, filePath: String?): Location {
  return Location(null, null, message, filePath?.let { Paths.get(it).fileName.toString() })
}

fun fileOnlyLocation(file: VirtualFile?, message: String?): Location {
  return Location(file, null, message)
}

fun resolvedLocation(file: VirtualFile?, range: RangeMarker?, message: String?): Location {
  return Location(file, range, message)
}

data class Location(val file: VirtualFile?, val range: RangeMarker?, val message: String?, val originalFileName: String? = null) {
  val isResolved = file != null && file.isValid && range != null && range.isValid
}
