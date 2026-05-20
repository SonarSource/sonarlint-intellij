/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.treeStructure.Tree
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import org.sonarlint.intellij.common.ui.ReadActionUtils.Companion.runReadActionSafely
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.ui.nodes.FindingNode
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode

/**
 * Copies one or more selected findings from a findings tree to the clipboard.
 * Each finding is formatted as "(line, col) message [ruleKey]" on its own line.
 */
class CopyFindingsAction : AnAction("Copy Finding(s)", "Copy selected findings to clipboard", AllIcons.Actions.Copy) {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val tree = getTree(e) ?: return
        val lines = tree.selectionPaths
            ?.mapNotNull { formatNode(it.lastPathComponent) }
            ?: return
        if (lines.isNotEmpty()) {
            val selection = StringSelection(lines.joinToString("\n"))
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
        }
    }

    override fun update(e: AnActionEvent) {
        val tree = getTree(e)
        e.presentation.isEnabled = tree != null &&
            tree.selectionPaths?.any { isFindingNode(it.lastPathComponent) } == true
    }

    private fun getTree(e: AnActionEvent): Tree? =
        e.dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT) as? Tree

    private fun isFindingNode(node: Any?) =
        node is FindingNode || node is LocalTaintVulnerability || node is LocalDependencyRisk

    private fun formatNode(node: Any?): String? {
        return when (node) {
            is IssueNode -> {
                val issue = node.issue()
                val coords = formatCoords(issue.file(), issue.range)
                "$coords${issue.message} [${issue.getRuleKey()}]"
            }
            is LiveSecurityHotspotNode -> {
                val hotspot = node.hotspot
                val coords = formatCoords(hotspot.file(), hotspot.range)
                "$coords${hotspot.message} [${hotspot.getRuleKey()}]"
            }
            is LocalTaintVulnerability -> {
                val coords = formatCoords(node.file(), node.rangeMarker())
                "$coords${node.message()} [${node.getRuleKey()}]"
            }
            is LocalDependencyRisk -> {
                "${node.packageName} ${node.packageVersion} [${node.getRuleKey()}]"
            }
            else -> null
        }
    }

    private fun formatCoords(file: VirtualFile?, rangeMarker: RangeMarker?): String {
        if (rangeMarker == null) return "(0, 0) "
        var result = "(-, -) "
        runReadActionSafely {
            if (!rangeMarker.isValid || file == null || !file.isValid) return@runReadActionSafely
            val doc = FileDocumentManager.getInstance().getCachedDocument(file) ?: return@runReadActionSafely
            val line = doc.getLineNumber(rangeMarker.startOffset)
            val offset = rangeMarker.startOffset - doc.getLineStartOffset(line)
            result = "(${line + 1}, $offset) "
        }
        return result
    }

}
