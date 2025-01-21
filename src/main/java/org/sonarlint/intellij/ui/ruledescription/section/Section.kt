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
package org.sonarlint.intellij.ui.ruledescription.section

import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput

class Section {
    val fragments: MutableList<SectionFragment> = mutableListOf()
    private val codeExampleFragmentsToDiffById = hashMapOf<String, MutableList<CodeExampleFragment>>()

    fun mergeOrAdd(htmlFragment: HtmlFragment) {
        val lastFragment = fragments.lastOrNull()
        if (lastFragment is HtmlFragment) {
            lastFragment.append(htmlFragment)
        } else {
            fragments.add(htmlFragment)
        }
    }

    fun add(codeExampleFragment: CodeExampleFragment) {
        fragments.add(codeExampleFragment)
        codeExampleFragment.diffId?.let { diffId ->
            val previousCodeFragments: MutableList<CodeExampleFragment>?
            if (codeExampleFragmentsToDiffById.containsKey(diffId)) {
                previousCodeFragments = codeExampleFragmentsToDiffById[diffId]
                if (previousCodeFragments!!.size > 1) {
                    GlobalLogOutput.get()
                        .log("More than 2 code examples with the same 'data-diff-id' value: $diffId", ClientLogOutput.Level.DEBUG)
                } else {
                    val previousCodeExample = previousCodeFragments.first()
                    previousCodeExample.diffTarget = codeExampleFragment
                    codeExampleFragment.diffTarget = previousCodeExample
                }
            } else {
                previousCodeFragments = mutableListOf()
                codeExampleFragmentsToDiffById[diffId] = previousCodeFragments
            }
            previousCodeFragments.add(codeExampleFragment)
        }
    }
}
