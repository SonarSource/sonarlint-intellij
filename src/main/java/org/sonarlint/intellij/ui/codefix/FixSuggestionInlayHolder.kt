/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.ui.codefix

import com.intellij.openapi.components.Service
import java.util.UUID
import org.sonarlint.intellij.ui.inlay.FixSuggestionInlayPanel
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixResponse

@Service(Service.Level.PROJECT)
class FixSuggestionInlayHolder {

    private val inlaySnippetsPerSuggestionId = mutableMapOf<UUID, MutableMap<Int, FixSuggestionInlayPanel>>()
    private val fixSuggestionsPerIssueId = mutableMapOf<UUID, SuggestFixResponse>()

    fun addFixSuggestion(issueId: UUID, fixSuggestion: SuggestFixResponse) {
        fixSuggestionsPerIssueId[issueId] = fixSuggestion
    }

    fun getFixSuggestion(issueId: UUID): SuggestFixResponse? {
        return fixSuggestionsPerIssueId[issueId]
    }

    fun shouldShowFix(suggestionId: UUID): Boolean {
        return inlaySnippetsPerSuggestionId[suggestionId] == null
    }

    fun shouldShowSnippet(suggestionId: UUID, index: Int): Boolean {
        return inlaySnippetsPerSuggestionId[suggestionId]?.containsKey(index) ?: true
    }

    fun addInlaySnippet(suggestionId: UUID, index: Int, inlaySnippet: FixSuggestionInlayPanel) {
        inlaySnippetsPerSuggestionId.computeIfAbsent(suggestionId) { mutableMapOf() }[index] = inlaySnippet
    }

    fun removeSnippet(suggestionId: UUID, index: Int) {
        inlaySnippetsPerSuggestionId[suggestionId]?.remove(index)
    }

}
