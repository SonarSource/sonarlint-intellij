/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.ui.ruledescription

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.PlainTextFileType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.RuleMonolithicDescriptionDto

class RuleDescriptionPanelTests : AbstractSonarLintLightTests() {

    @Test
    fun `removeAll releases rule code example viewers`() {
        val panel = RuleDescriptionPanel(project, testRootDisposable)
        val description = RuleMonolithicDescriptionDto("<p>Example</p><pre>val x = 1</pre>")
        panel.addMonolith(description, PlainTextFileType.INSTANCE)

        assertThat(sonarLintViewerCount()).isEqualTo(1)

        panel.removeAll()

        assertThat(sonarLintViewerCount()).isZero()
    }

    @Test
    fun `replacing rule description releases previous viewers`() {
        val panel = RuleDescriptionPanel(project, testRootDisposable)
        val first = RuleMonolithicDescriptionDto("<pre>first snippet</pre>")
        val second = RuleMonolithicDescriptionDto("<pre>second snippet</pre>")

        panel.addMonolith(first, PlainTextFileType.INSTANCE)
        panel.removeAll()
        panel.addMonolith(second, PlainTextFileType.INSTANCE)

        assertThat(sonarLintViewerCount()).isEqualTo(1)

        panel.removeAll()

        assertThat(sonarLintViewerCount()).isZero()
    }

    private fun sonarLintViewerCount(): Int {
        return EditorFactory.getInstance().allEditors.count {
            it.document.getUserData(RuleCodeSnippet.IS_SONARLINT_DOCUMENT) == true
        }
    }
}
