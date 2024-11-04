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
package org.sonarlint.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBTabbedPane
import java.util.UUID
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.editor.EditorDecorator
import org.sonarlint.intellij.finding.Flow
import org.sonarlint.intellij.finding.LiveFinding
import org.sonarlint.intellij.ui.tree.FlowsTree
import org.sonarlint.intellij.ui.tree.FlowsTreeModelBuilder


enum class FindingKind {
    ISSUE, SECURITY_HOTSPOT, MIX
}

class FindingDetailsPanel(private val project: Project, parentDisposable: Disposable, findingKind: FindingKind) : JBTabbedPane() {
    private lateinit var rulePanel: SonarLintRulePanel
    private lateinit var flowsTree: FlowsTree
    private lateinit var flowsTreeBuilder: FlowsTreeModelBuilder
    private val findingKindText: String = when(findingKind) {
        FindingKind.ISSUE -> "issue"
        FindingKind.SECURITY_HOTSPOT -> "Security Hotspot"
        else -> "finding"
    }

    init {
        createFlowsTree()
        createTabs(parentDisposable)
    }

    private fun createFlowsTree() {
        flowsTreeBuilder = FlowsTreeModelBuilder()
        val model = flowsTreeBuilder.createModel()
        flowsTree = FlowsTree(project, model)
        flowsTreeBuilder.clearFlows()
        flowsTree.emptyText.text = "No $findingKindText selected"
    }

    private fun createTabs(parentDisposable: Disposable) {
        // Flows panel with tree
        val flowsPanel = ScrollPaneFactory.createScrollPane(flowsTree, true)
        flowsPanel.verticalScrollBar.unitIncrement = 10

        // Rule panel
        rulePanel = SonarLintRulePanel(project, parentDisposable)
        insertTab("Rule", null, rulePanel, "Details about the rule", RULE_TAB_INDEX)
        insertTab("Locations", null, flowsPanel, "All locations involved in the $findingKindText", LOCATIONS_TAB_INDEX)
    }

    fun show(liveFinding: LiveFinding) {
        rulePanel.setSelectedFinding(liveFinding.module, liveFinding, liveFinding.getId())
        flowsTreeBuilder.populateForFinding(liveFinding)
        SonarLintUtils.getService(project, EditorDecorator::class.java).highlightFinding(liveFinding)
        flowsTree.emptyText.text = "Selected $findingKindText doesn't have flows"
        flowsTree.expandAll()
    }

    fun showServerOnlyIssue(
        module: Module,
        file: VirtualFile,
        issueKey: String,
        range: RangeMarker,
        flows: MutableList<Flow>,
        flowMessage: String
    ) {
        val issueId = UUID.fromString(issueKey)
        rulePanel.setSelectedFinding(module, null, issueId)
        flowsTreeBuilder.populateForFinding(file, range, flowMessage, flows)
        SonarLintUtils.getService(project, EditorDecorator::class.java).highlightRange(range)
        flowsTree.emptyText.text = "Selected $findingKindText doesn't have flows"
        flowsTree.expandAll()
    }

    fun clear() {
        flowsTreeBuilder.clearFlows()
        flowsTree.emptyText.text = "No $findingKindText selected"
        rulePanel.clear()
    }

    fun selectRulesTab() {
        selectedIndex = RULE_TAB_INDEX
    }

    fun selectLocationsTab() {
        selectedIndex = LOCATIONS_TAB_INDEX
    }

    companion object {
        private const val RULE_TAB_INDEX = 0
        private const val LOCATIONS_TAB_INDEX = 1
    }
}
