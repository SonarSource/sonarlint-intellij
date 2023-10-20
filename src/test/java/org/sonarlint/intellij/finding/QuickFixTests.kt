/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.finding.issue.aClientInputFile
import org.sonarlint.intellij.finding.issue.aFileEdit
import org.sonarlint.intellij.finding.issue.aQuickFix
import org.sonarlint.intellij.finding.issue.aTextEdit
import org.sonarlint.intellij.finding.issue.aTextRange

class QuickFixTests : AbstractSonarLintLightTests() {
    @Test
    fun should_convert_quick_fix_if_file_and_text_edit_are_valid() {
        val file = myFixture.configureByText("file.ext", "Text")
        val fix = aQuickFix(
            "Fix message",
            listOf(
                aFileEdit(
                    aClientInputFile(file, myFixture.getDocument(file)),
                    listOf(aTextEdit(aTextRange(1, 0, 1, 4), "newText"))
                )
            )
        )

        val convertedFix = convert(project, fix)

        assertThat(convertedFix).isNotNull
        assertThat(convertedFix!!.isApplicable(myFixture.getDocument(file))).isTrue
        assertThat(convertedFix.applied).isFalse
        assertThat(convertedFix.message).isEqualTo("Fix message")
        assertThat(convertedFix.virtualFileEdits).extracting({ it.target }).containsOnly(tuple(file.virtualFile))
        assertThat(convertedFix.virtualFileEdits[0].edits).extracting({ it.newText }).containsOnly(tuple("newText"))
        assertThat(convertedFix.virtualFileEdits[0].edits[0].rangeMarker)
            .extracting(
                { it.startOffset },
                { it.endOffset })
            .containsOnly(0, 4)
    }

    @Test
    fun should_not_convert_quick_fix_if_edit_range_line_overflows() {
        val file = myFixture.configureByText("file.ext", "Text")
        val fix = aQuickFix(
            "Fix message",
            listOf(
                aFileEdit(
                    aClientInputFile(file, myFixture.getDocument(file)),
                    listOf(aTextEdit(aTextRange(2, 0, 2, 1), "newText"))
                )
            )
        )

        val convertedFix = convert(project, fix)

        assertThat(convertedFix).isNull()
    }

    @Test
    fun should_not_convert_quick_fix_if_it_targets_several_files() {
        val file = myFixture.configureByText("file.ext", "Text")
        val file2 = myFixture.configureByText("file2.ext", "Text")
        val fix = aQuickFix(
            "Fix message",
            listOf(
                aFileEdit(
                    aClientInputFile(file, myFixture.getDocument(file)),
                    listOf(aTextEdit(aTextRange(1, 0, 1, 0), "newText"))
                ),
                aFileEdit(
                    aClientInputFile(file2, myFixture.getDocument(file2)),
                    listOf(aTextEdit(aTextRange(1, 0, 1, 0), "newText"))
                )
            )
        )

        val convertedFix = convert(project, fix)

        assertThat(convertedFix).isNull()
    }

    @Test
    fun should_not_convert_quick_fix_if_edit_range_line_offset_overflows() {
        val file = myFixture.configureByText("file.ext", "Text")
        val fix = aQuickFix(
            "Fix message",
            listOf(
                aFileEdit(
                    aClientInputFile(file, myFixture.getDocument(file)),
                    listOf(aTextEdit(aTextRange(1, 0, 1, 5), "newText"))
                )
            )
        )

        val convertedFix = convert(project, fix)

        assertThat(convertedFix).isNull()
    }

    @Test
    fun should_not_convert_quick_fix_if_document_has_changed() {
        val file = myFixture.configureByText("file.ext", "Text")
        val document = myFixture.getDocument(file)
        val fix = aQuickFix(
            "Fix message",
            listOf(aFileEdit(aClientInputFile(file, document), listOf(aTextEdit(aTextRange(1, 0, 1, 4), "newText"))))
        )
        myFixture.type("new content")

        val convertedFix = convert(project, fix)

        assertThat(convertedFix).isNull()
    }
}
