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
package org.sonarlint.intellij.ui.inlay

import com.intellij.openapi.components.Service
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixResponse

@Service(Service.Level.PROJECT)
class FixSuggestionInlayHolder {

    private val inlaySnippetsPerSuggestionId = mutableMapOf<String, MutableMap<Int, FixSuggestionInlayPanel>>()
    private val fixSuggestionsPerIssueId = mutableMapOf<String, SuggestFixResponse>()

    fun addFixSuggestion(issueId: String, fixSuggestion: SuggestFixResponse) {
        fixSuggestionsPerIssueId[issueId] = fixSuggestion
    }

    fun getFixSuggestion(issueId: String): SuggestFixResponse? {
        return fixSuggestionsPerIssueId[issueId]
    }

    fun shouldShowSnippet(suggestionId: String, index: Int): Boolean {
        return inlaySnippetsPerSuggestionId[suggestionId]?.containsKey(index) ?: true
    }

    fun addInlaySnippet(suggestionId: String, index: Int, inlaySnippet: FixSuggestionInlayPanel) {
        inlaySnippetsPerSuggestionId.computeIfAbsent(suggestionId) { mutableMapOf() }[index] = inlaySnippet
    }

    fun removeSnippet(suggestionId: String, index: Int) {
        inlaySnippetsPerSuggestionId[suggestionId]?.remove(index)
    }

    fun getPreviousInlay(suggestionId: String, currIndex: Int): FixSuggestionInlayPanel? {
        val inlays = inlaySnippetsPerSuggestionId[suggestionId] ?: return null
        val maxIndex = inlays.keys.maxOrNull() ?: return null

        for (index in (currIndex - 1) downTo 0) {
            inlays[index]?.let { return it }
        }

        for (index in maxIndex downTo currIndex + 1) {
            inlays[index]?.let { return it }
        }

        return null
    }

    fun getNextInlay(suggestionId: String, currIndex: Int): FixSuggestionInlayPanel? {
        val inlays = inlaySnippetsPerSuggestionId[suggestionId] ?: return null
        val maxIndex = inlays.keys.maxOrNull() ?: return null

        for (index in (currIndex + 1)..maxIndex) {
            inlays[index]?.let { return it }
        }

        for (index in 0 until currIndex) {
            inlays[index]?.let { return it }
        }

        return null
    }

}
