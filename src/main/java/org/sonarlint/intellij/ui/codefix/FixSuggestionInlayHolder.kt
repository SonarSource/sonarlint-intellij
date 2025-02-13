package org.sonarlint.intellij.ui.codefix

import com.intellij.openapi.components.Service
import org.sonarlint.intellij.fix.LocalFixSuggestion
import org.sonarlint.intellij.ui.inlay.FixSuggestionInlayPanel

@Service(Service.Level.PROJECT)
class FixSuggestionInlayHolder {

    private val inlaySnippetsPerSuggestionId = mutableMapOf<String, MutableMap<Int, FixSuggestionInlayPanel>>()
    private val fixSuggestionsPerIssueId = mutableMapOf<String, LocalFixSuggestion>()

    fun addFixSuggestion(issueId: String, fixSuggestion: LocalFixSuggestion) {
        fixSuggestionsPerIssueId[issueId] = fixSuggestion
    }

    fun getFixSuggestion(issueId: String): LocalFixSuggestion? {
        return fixSuggestionsPerIssueId[issueId]
    }

    fun shouldShowFix(suggestionId: String): Boolean {
        return inlaySnippetsPerSuggestionId[suggestionId] == null
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

}
