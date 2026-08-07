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
package org.sonarlint.intellij.util

import com.intellij.openapi.vfs.VirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.finding.sca.aDependencyRisk
import org.sonarlint.intellij.ui.filter.FilteredFindings
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails

class FixPromptBuilderTests {

    @Test
    fun `build includes header and finding details`() {
        val file = mockFile("/project/src/Foo.java")
        val issue = anIssue(file, message = "Null pointer dereference", ruleKey = "java:S2259", severity = IssueSeverity.MAJOR)
        val dependencyRisk = aDependencyRisk(DependencyRiskDto.Status.OPEN)

        val prompt = FixPromptBuilder.build(
            FilteredFindings(listOf(issue), emptyList(), emptyList(), listOf(dependencyRisk)),
            "Current File tab"
        )

        assertThat(prompt).contains("Please fix all the following SonarQube findings in this project.")
        assertThat(prompt).contains("Context: Current File tab")
        assertThat(prompt).contains("Total findings: 2 (1 issue(s), 1 dependency risk(s))")
        assertThat(prompt).contains("## Finding 1")
        assertThat(prompt).contains("Category: Code Issue")
        assertThat(prompt).contains("File: /project/src/Foo.java")
        assertThat(prompt).contains("Rule: java:S2259")
        assertThat(prompt).contains("Severity: MAJOR")
        assertThat(prompt).contains("Type: BUG")
        assertThat(prompt).contains("Message: Null pointer dereference")
        assertThat(prompt).contains("## Finding 2")
        assertThat(prompt).contains("Category: Dependency Risk")
        assertThat(prompt).contains("Package: ${dependencyRisk.packageName}")
        assertThat(prompt).contains("Version: ${dependencyRisk.packageVersion}")
    }

    @Test
    fun `build handles empty findings`() {
        val prompt = FixPromptBuilder.build(FilteredFindings(emptyList(), emptyList(), emptyList(), emptyList()), "Report tab")

        assertThat(prompt).contains("Context: Report tab")
        assertThat(prompt).contains("Total findings: 0 ()")
        assertThat(prompt).doesNotContain("## Finding")
    }

    private fun mockFile(path: String): VirtualFile {
        val file = Mockito.mock(VirtualFile::class.java)
        `when`(file.path).thenReturn(path)
        `when`(file.isValid).thenReturn(true)
        return file
    }

    private fun anIssue(
        file: VirtualFile,
        message: String,
        ruleKey: String,
        severity: IssueSeverity,
    ): LiveIssue {
        val dto = Mockito.mock(RaisedIssueDto::class.java)
        `when`(dto.primaryMessage).thenReturn(message)
        `when`(dto.ruleKey).thenReturn(ruleKey)
        `when`(dto.severityMode).thenReturn(Either.forLeft(StandardModeDetails(severity, RuleType.BUG)))
        return LiveIssue(null, dto, file, emptyList())
    }
}
