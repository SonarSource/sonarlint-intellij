/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.nodes.FileNode
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.nodes.SummaryNode
import org.sonarlint.intellij.ui.report.FindingSelectionManager
import org.sonarlint.intellij.ui.report.ReportTreeManager
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity

class CreateDevoxxGenieTasksAction(
    private val selectionManager: FindingSelectionManager,
    private val treeManager: ReportTreeManager
) : AnAction("Create DevoxxGenie Task(s)", "Create backlog tasks for selected issues", AllIcons.Actions.AddList) {

    override fun update(e: AnActionEvent) {
        val count = selectionManager.getSelectedCount()
        e.presentation.isEnabled = count > 0
        e.presentation.text = if (count > 0) "Create $count DevoxxGenie Task(s)" else "Create DevoxxGenie Task(s)"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val projectRoot = project.basePath ?: return
        val selectedIds = selectionManager.getSelectedIds()
        if (selectedIds.isEmpty()) return

        val issues = collectSelectedIssues(selectedIds)
        if (issues.isEmpty()) return

        val backlogDir = File(projectRoot, "backlog/tasks")
        backlogDir.mkdirs()

        val nextTaskNumber = nextTaskNumber(backlogDir)
        val createdDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        var createdCount = 0
        for ((index, issue) in issues.withIndex()) {
            val file = issue.file()
            val relativePath = computeRelativePath(file, projectRoot)
            val lineNumber = getLineNumber(issue)
            val fileName = sanitizeForFilename(file.name)
            val ruleKey = issue.getRuleKey()
            val sanitizedRule = sanitizeForFilename(ruleKey)
            val taskNumber = nextTaskNumber + index

            val taskFileName = buildTaskFileName(taskNumber, sanitizedRule, fileName, lineNumber)
            val taskFile = File(backlogDir, taskFileName)
            val content = buildTaskContent(issue, relativePath, lineNumber, ruleKey, file.name, taskNumber, createdDate)
            taskFile.writeText(content)
            createdCount++
        }

        // Refresh VFS so files appear in project tree
        LocalFileSystem.getInstance().refreshAndFindFileByPath(backlogDir.path)

        // Clear selections after creation
        selectionManager.clear()

        // Show balloon notification
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SonarQube for IDE")
            .createNotification(
                "DevoxxGenie Tasks Created",
                "Created $createdCount task(s) in <code>backlog/tasks/</code>",
                NotificationType.INFORMATION
            )
            .notify(project)
    }

    private fun collectSelectedIssues(selectedIds: Set<java.util.UUID>): List<LiveIssue> {
        val result = mutableListOf<LiveIssue>()
        for (builder in listOf(treeManager.issuesTreeBuilder, treeManager.oldIssuesTreeBuilder)) {
            val root = builder.model.root as? SummaryNode ?: continue
            for (i in 0 until root.childCount) {
                val fileNode = root.getChildAt(i) as? FileNode ?: continue
                for (j in 0 until fileNode.childCount) {
                    val issueNode = fileNode.getChildAt(j) as? IssueNode ?: continue
                    val issue = issueNode.issue()
                    if (selectedIds.contains(issue.getId())) {
                        result.add(issue)
                    }
                }
            }
        }
        return result
    }

    private fun computeRelativePath(file: VirtualFile, projectRoot: String): String {
        val absPath = file.path
        return if (absPath.startsWith(projectRoot)) {
            absPath.removePrefix(projectRoot).trimStart('/', '\\')
        } else {
            file.name
        }
    }

    private fun getLineNumber(issue: LiveIssue): Int {
        val range = issue.getRange() ?: return 0
        if (!range.isValid) return 0
        val document = FileDocumentManager.getInstance().getCachedDocument(issue.file()) ?: return 0
        return document.getLineNumber(range.startOffset) + 1
    }

    private fun sanitizeForFilename(input: String): String {
        return input.replace(Regex("[^a-zA-Z0-9]"), "-")
            .lowercase()
            .take(60)
            .trimEnd('-')
    }

    private fun nextTaskNumber(backlogDir: File): Int {
        val backlogRoot = backlogDir.parentFile
        val dirsToScan = listOf(
            File(backlogRoot, "tasks"),
            File(backlogRoot, "completed"),
            File(backlogRoot, "archive/tasks")
        )
        val maxId = dirsToScan
            .flatMap { it.listFiles { f -> f.extension == "md" }?.toList() ?: emptyList() }
            .mapNotNull { extractTaskNumber(it) }
            .maxOrNull() ?: 0
        return maxId + 1
    }

    private fun extractTaskNumber(file: File): Int? {
        return try {
            file.useLines { lines ->
                lines.take(20).firstOrNull { it.trimStart().startsWith("id:") }
                    ?.let { Regex("\\d+$").find(it)?.value?.toIntOrNull() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildTaskFileName(taskNumber: Int, sanitizedRule: String, fileName: String, lineNumber: Int): String {
        val baseName = "TASK-$taskNumber-sonar-$sanitizedRule-$fileName-l$lineNumber"
        return baseName.take(80) + ".md"
    }

    private fun priorityFromIssue(issue: LiveIssue): String {
        val highestImpact = issue.getHighestImpact()
        if (highestImpact != null) {
            return when (highestImpact) {
                ImpactSeverity.HIGH -> "high"
                ImpactSeverity.MEDIUM -> "medium"
                else -> "low"
            }
        }
        val severity = issue.getUserSeverity()
        if (severity != null) {
            return when (severity) {
                IssueSeverity.BLOCKER, IssueSeverity.CRITICAL -> "high"
                IssueSeverity.MAJOR -> "medium"
                else -> "low"
            }
        }
        return "medium"
    }

    private fun severityLabel(issue: LiveIssue): String {
        val highestImpact = issue.getHighestImpact()
        val highestQuality = issue.getHighestQuality()
        if (highestImpact != null && highestQuality != null) {
            return "${highestImpact.name.lowercase().replaceFirstChar { it.uppercase() }} impact on ${highestQuality.name.lowercase().replaceFirstChar { it.uppercase() }}"
        }
        val severity = issue.getUserSeverity()
        return severity?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Unknown"
    }

    private fun buildTaskContent(
        issue: LiveIssue,
        relativePath: String,
        lineNumber: Int,
        ruleKey: String,
        fileName: String,
        taskNumber: Int,
        createdDate: String
    ): String {
        val priority = priorityFromIssue(issue)
        val severity = severityLabel(issue)
        val message = issue.getMessage()
        val rulePrefix = ruleKey.substringBefore(':').lowercase()
        val ordinal = taskNumber * 1000

        return """---
id: TASK-$taskNumber
title: Fix $ruleKey in $fileName at line $lineNumber
status: To Do
priority: $priority
assignee: []
created_date: '$createdDate'
labels:
  - sonarqube
  - $rulePrefix
dependencies: []
references: []
documentation: []
ordinal: $ordinal
---

# Fix `$ruleKey`: $message

## Description

SonarQube for IDE detected a code quality issue.

- **Rule:** `$ruleKey`
- **File:** `$relativePath`
- **Line:** $lineNumber
- **Severity:** $severity
- **Issue:** $message

## Task

Fix the SonarQube issue `$ruleKey` at line $lineNumber in `$relativePath`.

## Acceptance Criteria

- [ ] Issue `$ruleKey` at `$fileName:$lineNumber` is resolved
- [ ] No new SonarQube issues introduced by the fix
- [ ] All existing tests continue to pass
"""
    }
}
