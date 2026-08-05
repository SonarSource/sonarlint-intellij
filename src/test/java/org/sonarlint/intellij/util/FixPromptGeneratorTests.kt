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
 * License along with the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
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

class FixPromptGeneratorTests {

    @Test
    fun `generate returns empty string when no findings`() {
        assertThat(FixPromptGenerator.generate(FilteredFindings(emptyList(), emptyList(), emptyList(), emptyList()))).isEmpty()
    }

    @Test
    fun `generate includes issue details`() {
        val issue = anIssue(
            path = "/project/src/Foo.java",
            message = "Remove this unused variable",
            ruleKey = "java:S1481",
            severity = IssueSeverity.MINOR,
            type = RuleType.CODE_SMELL
        )

        val prompt = FixPromptGenerator.generate(FilteredFindings(listOf(issue), emptyList(), emptyList(), emptyList()))

        assertThat(prompt).contains("Please fix the following SonarQube findings")
        assertThat(prompt).contains("## Finding 1 — Issue")
        assertThat(prompt).contains("**File:** /project/src/Foo.java")
        assertThat(prompt).contains("**Rule:** java:S1481 (CODE_SMELL)")
        assertThat(prompt).contains("**Severity:** MINOR")
        assertThat(prompt).contains("**Message:** Remove this unused variable")
        assertThat(prompt).contains("run the relevant tests")
    }

    @Test
    fun `generate includes dependency risk details`() {
        val risk = aDependencyRisk(DependencyRiskDto.Status.OPEN)

        val prompt = FixPromptGenerator.generate(FilteredFindings(emptyList(), emptyList(), emptyList(), listOf(risk)))

        assertThat(prompt).contains("## Finding 1 — Dependency Risk")
        assertThat(prompt).contains("**Package:** ${risk.packageName} ${risk.packageVersion}")
        assertThat(prompt).contains("dependency-risk:")
        assertThat(prompt).contains("Upgrade or replace the dependency")
    }

    @Test
    fun `countFindings returns total number of displayed findings`() {
        val issue = anIssue("/a.java", "msg", "java:S1", IssueSeverity.MAJOR, RuleType.BUG)
        val findings = FilteredFindings(listOf(issue), emptyList(), emptyList(), listOf(aDependencyRisk(DependencyRiskDto.Status.OPEN)))

        assertThat(FixPromptGenerator.countFindings(findings)).isEqualTo(2)
    }

    @Test
    fun `generate sorts issues by file path`() {
        val issueB = anIssue("/project/B.java", "Second", "java:S2", IssueSeverity.MAJOR, RuleType.BUG)
        val issueA = anIssue("/project/A.java", "First", "java:S1", IssueSeverity.MAJOR, RuleType.BUG)

        val prompt = FixPromptGenerator.generate(FilteredFindings(listOf(issueB, issueA), emptyList(), emptyList(), emptyList()))

        assertThat(prompt.indexOf("A.java")).isLessThan(prompt.indexOf("B.java"))
    }

    private fun anIssue(
        path: String,
        message: String,
        ruleKey: String,
        severity: IssueSeverity,
        type: RuleType,
    ): LiveIssue {
        val dto = Mockito.mock(RaisedIssueDto::class.java)
        `when`(dto.primaryMessage).thenReturn(message)
        `when`(dto.ruleKey).thenReturn(ruleKey)
        `when`(dto.severityMode).thenReturn(Either.forLeft(StandardModeDetails(severity, type)))
        val file = Mockito.mock(VirtualFile::class.java)
        `when`(file.path).thenReturn(path)
        `when`(file.isValid).thenReturn(true)
        return LiveIssue(null, dto, file, emptyList())
    }
}
