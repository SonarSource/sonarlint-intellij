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
package org.sonarlint.intellij.editor.actions

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import org.sonarlint.intellij.actions.AbstractSonarAction
import org.sonarlint.intellij.ui.ruledescription.RuleCodeSnippet
import org.sonarlint.intellij.ui.ruledescription.section.CodeExampleType

class DiffCodeExamplesAction(private val editor: Editor) :
    AbstractSonarAction("Diff", "Diff code examples", AllIcons.Actions.Diff) {

    override fun isVisible(e: AnActionEvent): Boolean {
        val codeExampleFragment = editor.getUserData(RuleCodeSnippet.CODE_EXAMPLE_FRAGMENT_KEY)
        return codeExampleFragment?.diffTarget != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val codeExampleFragment = editor.getUserData(RuleCodeSnippet.CODE_EXAMPLE_FRAGMENT_KEY)
        if (codeExampleFragment != null) {
            val thisExampleCode = codeExampleFragment.code
            val otherExampleCode = codeExampleFragment.diffTarget!!.code
            val (compliantCode, nonCompliantCode) = if (codeExampleFragment.type == CodeExampleType.Compliant) thisExampleCode to otherExampleCode else otherExampleCode to thisExampleCode
            DiffManager.getInstance().showDiff(
                e.project,
                SimpleDiffRequest(
                    "Diff Between Code Examples",
                    DiffContentFactory.getInstance().create(nonCompliantCode),
                    DiffContentFactory.getInstance().create(compliantCode),
                    "Non-compliant code",
                    "Compliant code"
                ),
                DiffDialogHints.MODAL
            )
        }
    }
}
