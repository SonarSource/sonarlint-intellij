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

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.editor.SonarLintHighlighting
import org.sonarlint.intellij.util.SonarLintUtils.getService


sealed class TaintVulnerabilitiesStatus {
  fun isEmpty() = count() == 0
  open fun count() = 0
}

object NoBinding : TaintVulnerabilitiesStatus()

object InvalidBinding : TaintVulnerabilitiesStatus()

data class FoundTaintVulnerabilities(val byFile: Map<VirtualFile, Collection<LocalTaintVulnerability>>) : TaintVulnerabilitiesStatus() {
  override fun count() = byFile.values.stream().mapToInt { it.size }.sum()
}

class TaintVulnerabilitiesPresenter(private val project: Project) {

  fun presentTaintVulnerabilitiesForOpenFiles() {
    if (project.isDisposed) {
      return
    }
    val status = TaintVulnerabilitiesLoader.getTaintVulnerabilitiesByOpenedFiles(project)
    getService(project, SonarLintToolWindow::class.java).populateTaintVulnerabilitiesTab(status)
    highlightTaintVulnerabilities(status)
    if (!status.isEmpty()) {
      // annotate the code with intention actions
      getService(project, CodeAnalyzerRestarter::class.java).refreshOpenFiles()
    }
  }

  private fun highlightTaintVulnerabilities(status: TaintVulnerabilitiesStatus) {
    val highlighter = getService(project, SonarLintHighlighting::class.java)
    when {
      status is FoundTaintVulnerabilities && !status.isEmpty() ->
        status.byFile.values.flatten().forEach { highlighter.highlight(it) }
      else -> highlighter.removeHighlights()
    }
  }
}