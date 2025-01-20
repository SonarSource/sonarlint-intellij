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
package org.sonarlint.intellij.editor

import com.intellij.openapi.command.WriteCommandAction
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.finding.QuickFix
import org.sonarlint.intellij.finding.RangeMarkerEdit
import org.sonarlint.intellij.finding.VirtualFileEdit

class ApplyQuickFixIntentionActionTests : AbstractSonarLintLightTests() {
    @Test
    fun should_normalize_line_endings_before_application() {
        val file = myFixture.configureByText("file.ext", "Text")
        val quickFix = QuickFix(
            "message",
            listOf(
                VirtualFileEdit(
                    file.virtualFile,
                    listOf(
                        RangeMarkerEdit(
                            myFixture.getDocument(file).createRangeMarker(0, 0),
                            "newText\rwith\r\nline\nreturns"
                        )
                    )
                )
            )
        )
        val intentionAction = ApplyQuickFixIntentionAction(quickFix, "ruleKey", false)

        WriteCommandAction.runWriteCommandAction(project) { intentionAction.invoke(project, myFixture.editor, file) }

        myFixture.checkResult("newText\nwith\nline\nreturnsText")
    }
}
