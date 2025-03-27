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
package org.sonarlint.intellij.nodejs

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.download.NodeJsDownloadableInterpreter
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter
import com.intellij.javascript.nodejs.interpreter.remote.NodeJsRemoteInterpreter
import com.intellij.javascript.nodejs.interpreter.wsl.WslNodeInterpreter
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.nio.file.Paths
import org.sonarlint.intellij.common.nodejs.NodeJsProvider

class JavaScriptNodeJsProvider : NodeJsProvider {

  override fun getNodeJsPathFor(project: Project): Path? {
    return NodeJsInterpreterManager.getInstance(project).interpreter?.let {
      return when (it) {
        is NodeJsLocalInterpreter -> Paths.get(it.interpreterSystemIndependentPath)
        is NodeJsRemoteInterpreter -> null
        is WslNodeInterpreter -> null
        is NodeJsDownloadableInterpreter -> null
        else -> null
      }
    }
  }

}
