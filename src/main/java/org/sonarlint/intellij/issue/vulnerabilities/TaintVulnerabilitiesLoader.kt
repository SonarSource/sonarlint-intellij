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
package org.sonarlint.intellij.issue.vulnerabilities

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.core.ModuleBindingManager
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.exception.InvalidBindingException
import org.sonarlint.intellij.util.SonarLintUtils.getService
import org.sonarlint.intellij.util.findModuleOf
import org.sonarlint.intellij.util.getOpenFiles
import org.sonarlint.intellij.util.getRelativePathOf
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue

private const val SECURITY_REPOSITORY_HINT = "security"

object TaintVulnerabilitiesLoader {

  private val LOG = Logger.getInstance(TaintVulnerabilitiesLoader::class.java)

  fun getTaintVulnerabilitiesByOpenedFiles(project: Project): TaintVulnerabilitiesStatus {
    if (!getSettingsFor(project).isBindingEnabled) return NoBinding
    val connectedEngine = getService(project, ProjectBindingManager::class.java).validConnectedEngine ?: return InvalidBinding
    return FoundTaintVulnerabilities(
      byFile = project.getOpenFiles().associateWith { getLocalTaintVulnerabilitiesForFile(it, project, connectedEngine) }
        .filter { it.value.isNotEmpty() }
    )
  }

  private fun getLocalTaintVulnerabilitiesForFile(file: VirtualFile, project: Project, connectedEngine: ConnectedSonarLintEngine): List<LocalTaintVulnerability> {
    return loadServerTaintVulnerabilitiesForFile(file, project, connectedEngine)
      .map { TaintVulnerabilityMatcher(project).match(it) }
  }

  private fun loadServerTaintVulnerabilitiesForFile(file: VirtualFile, project: Project, connectedEngine: ConnectedSonarLintEngine): List<ServerIssue> {
    val module = project.findModuleOf(file) ?: return emptyList()
    val moduleBindingManager = getService(module, ModuleBindingManager::class.java)
    val projectBinding = moduleBindingManager.binding
      ?: throw InvalidBindingException("Module ${module.name} is not bound")
    val filePath = project.getRelativePathOf(file)
    if (filePath == null) {
      LOG.error("Filepath for file ${file.canonicalPath} was not resolved.")
      return emptyList()
    }
    return connectedEngine.getServerIssues(projectBinding, filePath)
      .filter { it.ruleKey().contains(SECURITY_REPOSITORY_HINT) }
      .filter { it.resolution().isEmpty() }
  }
}
