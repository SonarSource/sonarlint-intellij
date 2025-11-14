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
package org.sonarlint.intellij.finding

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.finding.TextRangeMatcher.computeTextRangeForNotebook

class TextRangeMatcherTest {

    @Test
    fun should_add_markdown_lines_for_notebook() {
        val fileContent = """
            #%%
            t = 1
            #%% md
            test message
            #%%
            t = 2
            #%% raw
            raw
        """.trimIndent()

        val result = computeTextRangeForNotebook(fileContent, 3, 0, 3, 4)

        assertThat(result.startLine).isEqualTo(5)
        assertThat(result.startLineOffset).isEqualTo(0)
        assertThat(result.endLine).isEqualTo(5)
        assertThat(result.endLineOffset).isEqualTo(4)
    }

    @Test
    fun should_not_change_notebook_range_if_no_markdown() {
        val fileContent = """
            #%%
            t = 1
            #%%
            test message
            #%%
            t = 2
        """.trimIndent()

        val result = computeTextRangeForNotebook(fileContent, 5, 0, 5, 4)

        assertThat(result.startLine).isEqualTo(5)
        assertThat(result.startLineOffset).isEqualTo(0)
        assertThat(result.endLine).isEqualTo(5)
        assertThat(result.endLineOffset).isEqualTo(4)
    }

}
