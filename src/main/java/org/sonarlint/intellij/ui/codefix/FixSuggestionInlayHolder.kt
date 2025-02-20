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
