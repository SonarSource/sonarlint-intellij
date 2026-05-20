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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.treeStructure.Tree
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.sca.aDependencyRisk
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails

class CopyFindingsActionTests : AbstractSonarLintLightTests() {

    private lateinit var action: CopyFindingsAction
    private lateinit var mockEvent: AnActionEvent
    private lateinit var mockDataContext: DataContext
    private lateinit var presentation: Presentation

    @BeforeEach
    fun init() {
        action = CopyFindingsAction()
        presentation = Presentation()
        mockEvent = mock(AnActionEvent::class.java)
        mockDataContext = mock(DataContext::class.java)
        `when`(mockEvent.presentation).thenReturn(presentation)
        `when`(mockEvent.dataContext).thenReturn(mockDataContext)
    }

    @Test
    fun `actionPerformed - copies single issue with null range as (0, 0)`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Clipboard not available in headless environment")

        val tree = treeWithSelectedNodes(IssueNode(anIssueWithFile(message = "Null pointer dereference", ruleKey = "java:S2259")))
        `when`(mockDataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT)).thenReturn(tree)

        action.actionPerformed(mockEvent)

        assertThat(clipboardText()).isEqualTo("(0, 0) Null pointer dereference [java:S2259]")
    }

    @Test
    fun `actionPerformed - copies multiple issues, one per line`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Clipboard not available in headless environment")

        val tree = treeWithSelectedNodes(
            IssueNode(anIssueWithFile(message = "First issue", ruleKey = "java:S1")),
            IssueNode(anIssueWithFile(message = "Second issue", ruleKey = "java:S2"))
        )
        `when`(mockDataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT)).thenReturn(tree)

        action.actionPerformed(mockEvent)

        assertThat(clipboardText()).isEqualTo(
            "(0, 0) First issue [java:S1]\n(0, 0) Second issue [java:S2]"
        )
    }

    @Test
    fun `actionPerformed - copies dependency risk with package name version and rule key`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Clipboard not available in headless environment")

        val risk = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        val tree = treeWithSelectedNodes(risk)
        `when`(mockDataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT)).thenReturn(tree)

        action.actionPerformed(mockEvent)

        assertThat(clipboardText()).contains("[dependency-risk:vulnerability]")
    }

    @Test
    fun `actionPerformed - does nothing when tree has no selection`() {
        val tree = emptyTree()
        `when`(mockDataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT)).thenReturn(tree)

        // Should not throw
        action.actionPerformed(mockEvent)
    }

    // --- helpers ---

    private fun anIssueWithFile(message: String, ruleKey: String): LiveIssue {
        val dto = mock(RaisedIssueDto::class.java)
        `when`(dto.primaryMessage).thenReturn(message)
        `when`(dto.ruleKey).thenReturn(ruleKey)
        `when`(dto.severityMode).thenReturn(Either.forLeft(StandardModeDetails(IssueSeverity.MAJOR, RuleType.BUG)))
        val file = mock(VirtualFile::class.java)
        `when`(file.isValid).thenReturn(true)
        return LiveIssue(null, dto, file, emptyList())
    }

    /**
     * A Tree subclass that overrides getSelectionPaths() to return pre-set paths.
     * This lets path components be any object without needing a compatible TreeModel.
     */
    private class FakeTree(vararg nodes: Any) : Tree(DefaultTreeModel(DefaultMutableTreeNode())) {
        private val fakePaths = nodes.map { node ->
            when (node) {
                is DefaultMutableTreeNode -> TreePath(node.path)
                else -> TreePath(arrayOf(DefaultMutableTreeNode(), node))
            }
        }.toTypedArray().takeIf { it.isNotEmpty() }

        override fun getSelectionPaths() = fakePaths
    }

    private fun treeWithSelectedNodes(vararg nodes: Any) = FakeTree(*nodes)

    private fun emptyTree() = FakeTree()

    private fun clipboardText() =
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String

}
