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
package org.sonarlint.intellij.ui.currentfile.tree

import com.intellij.openapi.vfs.VirtualFile
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails

class SingleFileIssueTreeModelBuilderTests : AbstractSonarLintLightTests() {

    private fun liveIssue(vf: VirtualFile, id: UUID, serverKey: String?, message: String): LiveIssue {
        val dto = mock<RaisedIssueDto>()
        whenever(dto.id).thenReturn(id)
        whenever(dto.serverKey).thenReturn(serverKey)
        whenever(dto.primaryMessage).thenReturn(message)
        whenever(dto.severityMode).thenReturn(Either.forLeft(StandardModeDetails(IssueSeverity.MAJOR, RuleType.BUG)))
        return LiveIssue(null, dto, vf, emptyList())
    }

    @Test
    fun `findFindingByKey finds issues grouped by file, by serverKey and by id, across multiple file groups`() {
        val fileA = myFixture.configureByText("AFile.txt", "a").virtualFile
        val fileB = myFixture.configureByText("BFile.txt", "b").virtualFile
        val idB = UUID.randomUUID()
        val idD = UUID.randomUUID()
        val issueA = liveIssue(fileA, UUID.randomUUID(), "srvA", "A")
        val issueB = liveIssue(fileA, idB, null, "B")
        val issueC = liveIssue(fileB, UUID.randomUUID(), "srvC", "C")
        val issueD = liveIssue(fileB, idD, "dup", "D")
        // Same key as issueD but lives in the file group that is visited first: this checks that
        // findFindingByKey returns the first match in traversal order, not the last.
        val issueDup = liveIssue(fileA, UUID.randomUUID(), "dup", "A-dup")

        val builder = SingleFileIssueTreeModelBuilder(project, false)
        builder.updateModelWithScope(fileA, listOf(issueA, issueB, issueDup, issueC, issueD), true)

        assertThat(builder.findFindingByKey("srvA")).isEqualTo(issueA)
        assertThat(builder.findFindingByKey(idB.toString())).isEqualTo(issueB)
        assertThat(builder.findFindingByKey("srvC")).isEqualTo(issueC)
        assertThat(builder.findFindingByKey(idD.toString())).isEqualTo(issueD)
        assertThat(builder.findFindingByKey("dup")).isEqualTo(issueDup)
        assertThat(builder.findFindingByKey("does-not-exist")).isNull()
    }

    @Test
    fun `findFindingByKey finds issues in flat mode without file grouping`() {
        val file = myFixture.configureByText("Flat.txt", "a").virtualFile
        val idB = UUID.randomUUID()
        val issueA = liveIssue(file, UUID.randomUUID(), "srvA", "A")
        val issueB = liveIssue(file, idB, null, "B")
        val issueC = liveIssue(file, UUID.randomUUID(), "srvC", "C")

        val builder = SingleFileIssueTreeModelBuilder(project, false)
        builder.updateModelWithScope(file, listOf(issueA, issueB, issueC), false)

        assertThat(builder.findFindingByKey("srvA")).isEqualTo(issueA)
        assertThat(builder.findFindingByKey(idB.toString())).isEqualTo(issueB)
        assertThat(builder.findFindingByKey("srvC")).isEqualTo(issueC)
        assertThat(builder.findFindingByKey("nope")).isNull()
    }

    @Test
    fun `findFindingByKey returns null when the tree is empty`() {
        val file = myFixture.configureByText("Empty.txt", "a").virtualFile
        val builder = SingleFileIssueTreeModelBuilder(project, false)
        builder.updateModelWithScope(file, emptyList(), true)

        assertThat(builder.findFindingByKey("anything")).isNull()
    }

    @Test
    fun `removeFinding removes only the matching issue from a grouped tree`() {
        val fileA = myFixture.configureByText("AFile2.txt", "a").virtualFile
        val fileB = myFixture.configureByText("BFile2.txt", "b").virtualFile
        val issueA1 = liveIssue(fileA, UUID.randomUUID(), "srvA1", "A1")
        val issueA2 = liveIssue(fileA, UUID.randomUUID(), "srvA2", "A2")
        val issueB = liveIssue(fileB, UUID.randomUUID(), "srvB", "B")

        val builder = SingleFileIssueTreeModelBuilder(project, false)
        builder.updateModelWithScope(fileA, listOf(issueA1, issueA2, issueB), true)

        builder.removeFinding(issueA1)

        assertThat(builder.findFindingByKey("srvA1")).isNull()
        assertThat(builder.findFindingByKey("srvA2")).isEqualTo(issueA2)
        assertThat(builder.findFindingByKey("srvB")).isEqualTo(issueB)
    }

    @Test
    fun `removeFinding does not throw when removing the last issue of a file group, and prunes the empty group`() {
        val fileA = myFixture.configureByText("AFile3.txt", "a").virtualFile
        val fileB = myFixture.configureByText("BFile3.txt", "b").virtualFile
        val issueA = liveIssue(fileA, UUID.randomUUID(), "srvA", "A")
        val issueB = liveIssue(fileB, UUID.randomUUID(), "srvB", "B")

        val builder = SingleFileIssueTreeModelBuilder(project, false)
        builder.updateModelWithScope(fileA, listOf(issueA, issueB), true)

        builder.removeFinding(issueA)

        assertThat(builder.findFindingByKey("srvA")).isNull()
        assertThat(builder.findFindingByKey("srvB")).isEqualTo(issueB)
        assertThat(builder.summaryNode.childCount).isEqualTo(1)
    }
}
