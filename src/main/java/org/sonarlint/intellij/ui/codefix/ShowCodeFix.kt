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
package org.sonarlint.intellij.ui.codefix

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.finding.LiveFinding
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto


data class ShowCodeFix<T : LiveFinding>(
    val module: Module,
    val ruleKey: String,
    val findingKey: String,
    val file: VirtualFile,
    val textRange: TextRangeDto,
    val type: Class<T>,
)
