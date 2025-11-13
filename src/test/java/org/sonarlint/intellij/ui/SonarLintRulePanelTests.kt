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
package org.sonarlint.intellij.ui

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.finding.Finding
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcErrorCode
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.EffectiveIssueDetailsDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.GetEffectiveIssueDetailsResponse
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.EffectiveRuleDetailsDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetEffectiveRuleDetailsResponse

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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted {
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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted {
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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted {
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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted {
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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted {
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

        await().atMost(2, TimeUnit.SECONDS).untilAsserted {
            verify(backendService).getEffectiveIssueDetails(module, issueId)
            verify(backendService, never()).getEffectiveRuleDetails(any(), any(), any())
        }
    }

}

