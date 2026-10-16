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
package org.sonarlint.intellij.ui

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import java.util.UUID
import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.Finding
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.codefix.CodeFixTabPanel
import org.sonarlint.intellij.ui.ruledescription.RuleDescriptionPanel
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.EffectiveIssueDetailsDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.GetEffectiveIssueDetailsResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleMonolithicDescriptionDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType
import org.sonarsource.sonarlint.core.rpc.protocol.common.StandardModeDetails

class SonarLintRulePanelTests : AbstractSonarLintLightTests() {

    private lateinit var backendService: BackendService
    private lateinit var panel: SonarLintRulePanel

    @BeforeEach
    fun preparation() {
        backendService = mock()
        replaceApplicationService(BackendService::class.java, backendService)
        panel = SonarLintRulePanel(project, testRootDisposable)
    }

    @Test
    fun `should load issue details successfully`() {
        val issueId = UUID.randomUUID()
        val issueDetails = mock<EffectiveIssueDetailsDto>()
        val response = GetEffectiveIssueDetailsResponse(issueDetails)
        whenever(backendService.getEffectiveIssueDetails(module, issueId))
            .thenReturn(CompletableFuture.completedFuture(response))

        panel.setSelectedFinding(module, null, issueId, false)

        awaitUi {
            verify(backendService).getEffectiveIssueDetails(module, issueId)
        }
    }

    @Test
    fun `should fallback to rule details when issue not found and finding has rule key`() {
        val issueId = UUID.randomUUID()
        val ruleKey = "java:S1234"
        val contextKey = "context"
        val finding = mock<Finding>()
        val ruleDetails = mock<EffectiveRuleDetailsDto>()
        val ruleResponse = GetEffectiveRuleDetailsResponse(ruleDetails)

        whenever(finding.getRuleKey()).thenReturn(ruleKey)
        whenever(finding.getRuleDescriptionContextKey()).thenReturn(contextKey)

        val issueNotFoundError = RuntimeException().apply {
            initCause(ResponseErrorException(ResponseError(
                SonarLintRpcErrorCode.ISSUE_NOT_FOUND,
                "Issue not found",
                null
            )))
        }

        whenever(backendService.getEffectiveIssueDetails(module, issueId))
            .thenReturn(CompletableFuture.failedFuture(issueNotFoundError))
        whenever(backendService.getEffectiveRuleDetails(module, ruleKey, contextKey))
            .thenReturn(CompletableFuture.completedFuture(ruleResponse))

        panel.setSelectedFinding(module, finding, issueId, false)

        awaitUi {
            verify(backendService).getEffectiveIssueDetails(module, issueId)
            verify(backendService).getEffectiveRuleDetails(module, ruleKey, contextKey)
        }
    }

    @Test
    fun `should handle error when issue not found but no finding available`() {
        val issueId = UUID.randomUUID()
        val issueNotFoundError = RuntimeException().apply {
            initCause(ResponseErrorException(ResponseError(
                SonarLintRpcErrorCode.ISSUE_NOT_FOUND,
                "Issue not found",
                null
            )))
        }

        whenever(backendService.getEffectiveIssueDetails(module, issueId))
            .thenReturn(CompletableFuture.failedFuture(issueNotFoundError))

        panel.setSelectedFinding(module, null, issueId, false)

        awaitUi {
            verify(backendService).getEffectiveIssueDetails(module, issueId)
            verify(backendService, never()).getEffectiveRuleDetails(any(), any(), any())
        }
    }

    @Test
    fun `should handle error when issue not found but finding has no rule key`() {
        val issueId = UUID.randomUUID()
        val finding = mock<Finding>()
        val issueNotFoundError = RuntimeException().apply {
            initCause(ResponseErrorException(ResponseError(
                SonarLintRpcErrorCode.ISSUE_NOT_FOUND,
                "Issue not found",
                null
            )))
        }

        whenever(finding.getRuleKey()).thenReturn(null)

        whenever(backendService.getEffectiveIssueDetails(module, issueId))
            .thenReturn(CompletableFuture.failedFuture(issueNotFoundError))

        panel.setSelectedFinding(module, finding, issueId, false)

        awaitUi {
            verify(backendService).getEffectiveIssueDetails(module, issueId)
            verify(backendService, never()).getEffectiveRuleDetails(any(), any(), any())
        }
    }

    @Test
    fun `should handle fallback failure when rule details cannot be loaded`() {
        val issueId = UUID.randomUUID()
        val ruleKey = "java:S1234"
        val finding = mock<Finding>()
        val ruleError = RuntimeException("Rule not found")

        whenever(finding.getRuleKey()).thenReturn(ruleKey)
        whenever(finding.getRuleDescriptionContextKey()).thenReturn(null)

        val issueNotFoundError = RuntimeException().apply {
            initCause(ResponseErrorException(ResponseError(
                SonarLintRpcErrorCode.ISSUE_NOT_FOUND,
                "Issue not found",
                null
            )))
        }

        whenever(backendService.getEffectiveIssueDetails(module, issueId))
            .thenReturn(CompletableFuture.failedFuture(issueNotFoundError))
        whenever(backendService.getEffectiveRuleDetails(module, ruleKey, null))
            .thenReturn(CompletableFuture.failedFuture(ruleError))

        panel.setSelectedFinding(module, finding, issueId, false)

        awaitUi {
            verify(backendService).getEffectiveIssueDetails(module, issueId)
            verify(backendService).getEffectiveRuleDetails(module, ruleKey, null)
        }
    }

    @Test
    fun `should handle non-issue-not-found errors without fallback`() {
        val issueId = UUID.randomUUID()
        val genericError = RuntimeException("Generic error")

        whenever(backendService.getEffectiveIssueDetails(module, issueId))
            .thenReturn(CompletableFuture.failedFuture(genericError))

        panel.setSelectedFinding(module, null, issueId, false)

        awaitUi {
            verify(backendService).getEffectiveIssueDetails(module, issueId)
            verify(backendService, never()).getEffectiveRuleDetails(any(), any(), any())
        }
    }

    @Test
    fun `should ignore pending issue success after parent disposal`() {
        val owner = Disposer.newDisposable()
        Disposer.register(testRootDisposable, owner)
        val ownedPanel = SonarLintRulePanel(project, owner)
        val issueId = UUID.randomUUID()
        val result = CompletableFuture<GetEffectiveIssueDetailsResponse>()
        val requested = CompletableFuture<Void>()
        whenever(backendService.getEffectiveIssueDetails(module, issueId)).doAnswer {
            requested.complete(null)
            result
        }
        ownedPanel.setSelectedFinding(module, mock(), issueId, true)
        awaitUi { assertThat(requested.isDone).isTrue()
            assertThat(result.numberOfDependents).isGreaterThanOrEqualTo(2)
        }
        Disposer.dispose(owner)
        result.complete(GetEffectiveIssueDetailsResponse(mock()))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertThat(project.isDisposed).isFalse()
        assertThat(field(ownedPanel, "issueDetails")).isNull()
        assertThat(field(ownedPanel, "finding")).isNull()
        assertThat((field(ownedPanel, "descriptionPanel") as javax.swing.JComponent).componentCount).isZero()
    }

    @Test
    fun `should not fall back after owner closes with a pending issue request`() {
        val owner = Disposer.newDisposable()
        Disposer.register(testRootDisposable, owner)
        val ownedPanel = SonarLintRulePanel(project, owner)
        val issueId = UUID.randomUUID()
        val result = CompletableFuture<GetEffectiveIssueDetailsResponse>()
        val requested = CompletableFuture<Void>()
        val finding = mock<Finding>()
        whenever(finding.getRuleKey()).thenReturn("java:S1234")
        whenever(backendService.getEffectiveIssueDetails(module, issueId)).doAnswer {
            requested.complete(null)
            result
        }
        ownedPanel.setSelectedFinding(module, finding, issueId, true)
        awaitUi { assertThat(requested.isDone).isTrue()
            assertThat(result.numberOfDependents).isGreaterThanOrEqualTo(2)
        }
        Disposer.dispose(owner)
        result.completeExceptionally(RuntimeException(ResponseErrorException(ResponseError(
            SonarLintRpcErrorCode.ISSUE_NOT_FOUND, "Issue not found", null
        ))))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        verify(backendService, never()).getEffectiveRuleDetails(any(), any(), any())
        assertThat(field(ownedPanel, "issueDetails")).isNull()
        assertThat(field(ownedPanel, "ruleDetails")).isNull()
        assertThat(project.isDisposed).isFalse()
    }

    @Test
    fun `should ignore rule success queued before owner disposal`() {
        val owner = Disposer.newDisposable()
        Disposer.register(testRootDisposable, owner)
        val ownedPanel = SonarLintRulePanel(project, owner)
        val result = CompletableFuture<GetEffectiveRuleDetailsResponse>()
        val requested = CompletableFuture<Void>()
        whenever(backendService.getEffectiveRuleDetails(module, "java:S1234", null)).doAnswer {
            requested.complete(null)
            result
        }
        ownedPanel.setSelectedFinding(module, "java:S1234")
        awaitUi { assertThat(requested.isDone).isTrue()
            assertThat(result.numberOfDependents).isGreaterThanOrEqualTo(2)
        }
        result.complete(GetEffectiveRuleDetailsResponse(mock()))
        Disposer.dispose(owner)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertThat(field(ownedPanel, "ruleDetails")).isNull()
        assertThat(field(ownedPanel, "ruleKey")).isNull()
        assertThat(project.isDisposed).isFalse()
    }


    @Test
    fun `should ignore queued issue success and code fix after owner disposal`() {
        val owner = Disposer.newDisposable()
        Disposer.register(testRootDisposable, owner)
        val ownedPanel = SonarLintRulePanel(project, owner)
        val issueId = UUID.randomUUID()
        val result = CompletableFuture<GetEffectiveIssueDetailsResponse>()
        whenever(backendService.getEffectiveIssueDetails(module, issueId)).thenReturn(result)
        ownedPanel.setSelectedFinding(module, mock(), issueId, true)
        awaitUi { assertThat(result.numberOfDependents).isGreaterThanOrEqualTo(2) }
        result.complete(GetEffectiveIssueDetailsResponse(mock()))
        Disposer.dispose(owner)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertThat(field(ownedPanel, "issueDetails")).isNull()
        assertThat((field(ownedPanel, "descriptionPanel") as javax.swing.JComponent).componentCount).isZero()
        assertThat(project.isDisposed).isFalse()
    }


    @Test
    fun `should drop nested code fix generation queued before owner closure`() {
        val owner = Disposer.newDisposable()
        Disposer.register(testRootDisposable, owner)
        val ownedPanel = SonarLintRulePanel(project, owner)
        val issueId = UUID.randomUUID()
        val file = createTestFile("CodeFix.java", "class CodeFix {}")
        val finding = mock<LiveIssue>()
        whenever(finding.getId()).thenReturn(issueId)
        whenever(finding.file()).thenReturn(file)
        whenever(finding.isAiCodeFixable()).thenReturn(true)
        val details = mock<EffectiveIssueDetailsDto>()
        whenever(details.name).thenReturn("AI-fixable rule")
        whenever(details.ruleKey).thenReturn("java:S1234")
        whenever(details.language).thenReturn(Language.JAVA)
        whenever(details.severityDetails).thenReturn(Either.forLeft(StandardModeDetails(IssueSeverity.MAJOR, RuleType.BUG)))
        whenever(details.description).thenReturn(Either.forLeft(RuleMonolithicDescriptionDto("<p>Rule description</p>")))
        whenever(details.params).thenReturn(emptyList())
        val result = CompletableFuture<GetEffectiveIssueDetailsResponse>()
        whenever(backendService.getEffectiveIssueDetails(module, issueId)).thenReturn(result)
        val queuedGeneration = mutableListOf<Runnable>()

        // Static mocks are local to this EDT: background detail loading still uses the real pool.
        mockStatic(Class.forName("org.sonarlint.intellij.util.ThreadUtilsKt")).use { pooledTasks ->
            pooledTasks.`when`<Unit> { runOnPooledThread(eq(project), any()) }.thenAnswer { invocation ->
                queuedGeneration.add(invocation.getArgument(1))
                null
            }
            ownedPanel.setSelectedFinding(module, finding, issueId, true)
            awaitUi { assertThat(result.numberOfDependents).isGreaterThanOrEqualTo(2) }
            result.complete(GetEffectiveIssueDetailsResponse(details))
            awaitUi { assertThat(queuedGeneration).hasSize(1) }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

            val description = field(ownedPanel, "descriptionPanel") as RuleDescriptionPanel
            val codeFix = field(description, "codeFixTab") as CodeFixTabPanel
            val visibleCards = codeFix.components.map { it.isVisible }
            assertThat(description.componentCount).isPositive()
            Disposer.dispose(owner)
            assertThat(field(description, "codeFixTab")).isNull()
            assertThat(field(description, "sectionsTabs")).isNull()
            queuedGeneration.single().run()
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

            verify(backendService, never()).suggestAiCodeFixSuggestion(any(), any())
            assertThat(codeFix.components.map { it.isVisible }).isEqualTo(visibleCards)
            assertThat(description.componentCount).isZero()
            assertThat(project.isDisposed).isFalse()
        }
    }

    private fun awaitUi(assertions: () -> Unit) {
        PlatformTestUtil.waitWithEventsDispatching("Rule details request", {
            try {
                assertions()
                true
            } catch (_: AssertionError) {
                false
            }
        }, 2)
        assertions()
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)

}

