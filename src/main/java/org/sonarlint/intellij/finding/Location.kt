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
package org.sonarlint.intellij.finding

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.tree.injected.changesHandler.range
import java.nio.file.Path
import java.util.regex.Pattern
import org.apache.commons.codec.digest.DigestUtils
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.computeReadActionSafely

fun unknownLocation(message: String?, filePath: Path?): Location {
  return Location(null, null, message, filePath?.fileName.toString(), null)
}

fun fileOnlyLocation(file: VirtualFile?, message: String?): Location {
  return Location(file, null, message, null, null)
}

fun resolvedLocation(file: VirtualFile?, range: RangeMarker?, message: String?, textRangeHash: String?): Location {
  return Location(file, range, message, null, textRangeHash)
}

data class Location(val file: VirtualFile?, val range: RangeMarker?, val message: String?, val originalFileName: String? = null, val textRangeHash: String?) {
  fun exists() = file != null && file.isValid && range != null && range.isValid && range.startOffset != range.endOffset
  fun codeMatches() = exists() && (textRangeHash == null || textRangeHash == computeReadActionSafely { hash(range!!.document.getText(range.range)) })
}

private val MATCH_ALL_WHITESPACES = Pattern.compile("\\s")

private fun hash(codeSnippet: String): String {
  val codeSnippetWithoutWhitespaces = MATCH_ALL_WHITESPACES.matcher(codeSnippet).replaceAll("")
  return DigestUtils.md5Hex(codeSnippetWithoutWhitespaces)
}
