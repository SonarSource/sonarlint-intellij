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

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.stream.Collectors
import org.apache.commons.codec.digest.DigestUtils
import org.sonarlint.intellij.common.ui.ReadActionUtils
import org.sonarlint.intellij.util.ProjectUtils.tryFindFile
import org.sonarsource.sonarlint.core.rpc.protocol.common.FlowDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto


data class ShowFinding<T : Finding>(
    val module: Module,
    val ruleKey: String,
    val findingKey: String,
    val file: VirtualFile,
    val textRange: TextRangeDto,
    val codeSnippet: String?,
    val flows: List<Flow>,
    val flowMessage: String,
    val type: Class<T>,
) {

    companion object {

        fun handleFlows(project: Project, flows: List<FlowDto>): List<Flow> {
            val matcher = TextRangeMatcher(project)
            return flows.map { flow ->
                flow.locations.stream().map {
                    tryFindFile(project, it.ideFilePath)?.let { file ->
                        it.codeSnippet?.let { _ ->
                            val rangeMarker = ReadActionUtils.Companion.computeReadActionSafely(project) {
                                matcher.matchWithCode(file, it.textRange, it.codeSnippet)
                            }
                            val textRangeHashString = DigestUtils.md5Hex(it.codeSnippet.replace("[\\s]".toRegex(), ""))
                            resolvedLocation(file, rangeMarker, it.message, textRangeHashString)
                        } ?: fileOnlyLocation(file, it.message)
                    }
                }.filter { it != null }.collect(Collectors.toList()).apply { reverse() }
            }.mapIndexed { index, locations -> Flow(index + 1, locations) }.toList()
        }

    }

}
