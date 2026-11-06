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
package org.sonarlint.intellij.ui.report

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintUILightTests
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.LiveFindings
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.fixtures.readOnEdt
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedIssueDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails

class ReportTabManagerLightTests : AbstractSonarLintUILightTests() {
    private lateinit var manager: ReportTabManager
    private val instant = Instant.parse("2026-01-02T10:15:00Z")

    @BeforeEach
    fun fixClock() {
        manager = ReportTabManager(project)
        replaceProjectService(ReportTabManager::class.java, manager)
        manager.clock = Clock.fixed(instant, ZoneOffset.UTC)
    }

    @Test
    fun `same minute loading reports keep results progress and selection independent`(): Unit = readOnEdt {
        val firstTitle = manager.createLoadingReportTab("first", 3)
        val secondTitle = manager.createLoadingReportTab("second", 3)
        assertThat(firstTitle).isEqualTo(secondTitle).isNotNull()
        val (first, second) = mainToolWindow.reports
        assertThat(first.isSelected).isFalse()
        assertThat(second.isSelected).isTrue()
        assertThat(manager.createLoadingReportTab("first", 3)).isEqualTo(firstTitle)
        assertThat(mainToolWindow.reports).hasSize(2)
        manager.updateOrCreateReportTab("first", result("first module"), 1, 3)
        assertThat(first.messages).containsExactly("first module")
        assertThat(second.messages).isEmpty()
        assertThat(second.statusTexts).contains("Analysis ongoing • 3 modules remaining")
        manager.updateOrCreateReportTab("second", result("second module"), 1, 3)
        manager.updateOrCreateReportTab("first", result("first final module"), 3, 3)
        assertThat(first.messages).containsExactlyInAnyOrder("first module", "first final module")
        assertThat(second.messages).containsExactly("second module")
        assertThat(first.statusTexts).isEmpty()
        assertThat(second.statusTexts).isNotEmpty()
        assertThat(first.isSelected).isFalse()
        assertThat(second.isSelected).isTrue()
    }

    @Test
    fun `fallback and standalone reports with duplicate titles retain separate results`(): Unit = readOnEdt {
        manager.updateOrCreateReportTab("first", result("first"))
        manager.updateOrCreateReportTab("second", result("second"))
        manager.createReportTab(result("standalone"))
        manager.updateOrCreateReportTab("first", result("more first"))
        val reports = mainToolWindow.reports
        assertThat(reports).hasSize(3)
        assertThat(reports.map { it.title }.distinct()).hasSize(1)
        assertThat(reports[0].messages).containsExactlyInAnyOrder("first", "more first")
        assertThat(reports[1].messages).containsExactly("second")
        assertThat(reports[2].messages).containsExactly("standalone")
        assertThat(reports[2].isSelected).isTrue()
    }

    @Test
    fun `refresh visits every report with a duplicate title`() {
        val refreshCounts = readOnEdt {
            manager.createReportTab(result("first"))
            manager.createReportTab(result("second"))
            mainToolWindow.reports.map { it.recordRefreshes() }
        }
        getService(project, org.sonarlint.intellij.actions.SonarLintToolWindow::class.java).refreshViews()
        awaitUntilAsserted {
            assertThat(refreshCounts.map { it() }).containsExactly(1, 1)
        }
    }

    private fun result(message: String): AnalysisResult {
        val file = myFixture.addFileToProject("${UUID.randomUUID()}.txt", "text").virtualFile
        val dto = mock(RaisedIssueDto::class.java)
        whenever(dto.id).thenReturn(UUID.randomUUID())
        whenever(dto.primaryMessage).thenReturn(message)
        whenever(dto.ruleKey).thenReturn("test:rule")
        whenever(dto.severityMode).thenReturn(Either.forLeft(StandardModeDetails(IssueSeverity.MAJOR, RuleType.BUG)))
        val issue = LiveIssue(module, dto, file, emptyList())
        return AnalysisResult(UUID.randomUUID(), LiveFindings(mapOf(file to listOf(issue)), emptyMap()), listOf(file), instant)
    }
}
