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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.sonarsource.sonarlint.core.rpc.protocol.backend.remediation.aicodefix.SuggestFixResponse

class FixSuggestionInlayHolderTests {

    private lateinit var holder: FixSuggestionInlayHolder

    @BeforeEach
    fun setUp() {
        holder = FixSuggestionInlayHolder()
    }

    @Test
    fun `should add and retrieve fix suggestion`() {
        val issueId = "issue1"
        val fixSuggestion = Mockito.mock(SuggestFixResponse::class.java)

        holder.addFixSuggestion(issueId, fixSuggestion)

        assertThat(fixSuggestion).isEqualTo(holder.getFixSuggestion(issueId))
    }

    @Test
    fun `should return null for non-existent fix suggestion`() {
        assertThat(holder.getFixSuggestion("nonExistentIssue")).isNull()
    }

    @Test
    fun `should show snippet by default`() {
        assertThat(holder.shouldShowSnippet("suggestion1", 0)).isTrue()
    }

    @Test
    fun `should add and remove inlay snippet`() {
        val suggestionId = "suggestion1"
        val index = 0
        val inlaySnippet = Mockito.mock(FixSuggestionInlayPanel::class.java)

        holder.addInlaySnippet(suggestionId, index, inlaySnippet)
        holder.removeSnippet(suggestionId, index)

        assertThat(holder.shouldShowSnippet(suggestionId, index)).isFalse()
    }

    @Test
    fun `should get previous inlay`() {
        val suggestionId = "suggestion1"
        val inlaySnippet1 = Mockito.mock(FixSuggestionInlayPanel::class.java)
        val inlaySnippet2 = Mockito.mock(FixSuggestionInlayPanel::class.java)

        holder.addInlaySnippet(suggestionId, 0, inlaySnippet1)
        holder.addInlaySnippet(suggestionId, 1, inlaySnippet2)

        assertThat(inlaySnippet1).isEqualTo(holder.getPreviousInlay(suggestionId, 1))
    }

    @Test
    fun `should get next inlay`() {
        val suggestionId = "suggestion1"
        val inlaySnippet1 = Mockito.mock(FixSuggestionInlayPanel::class.java)
        val inlaySnippet2 = Mockito.mock(FixSuggestionInlayPanel::class.java)

        holder.addInlaySnippet(suggestionId, 0, inlaySnippet1)
        holder.addInlaySnippet(suggestionId, 1, inlaySnippet2)

        assertThat(inlaySnippet2).isEqualTo(holder.getNextInlay(suggestionId, 0))
    }

}
