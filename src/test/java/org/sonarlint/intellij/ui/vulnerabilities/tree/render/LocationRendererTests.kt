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
package org.sonarlint.intellij.ui.vulnerabilities.tree.render

import com.intellij.openapi.editor.RangeMarker
import com.intellij.ui.SimpleTextAttributes
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.finding.Flow
import org.sonarlint.intellij.finding.FragmentLocation
import org.sonarlint.intellij.finding.Location
import org.sonarlint.intellij.ui.tree.TreeCellRenderer

class LocationRendererTests : AbstractSonarLintLightTests() {
    private lateinit var range: RangeMarker

    @BeforeEach
    fun prepare() {
        myFixture.configureByText("file.txt", "my document test")
        range = myFixture.getDocument(myFixture.file).createRangeMarker(3, 10)
    }

    @Test
    fun `renders coordinates from the range marker document`() {
        val renderer = mock<TreeCellRenderer>()

        LocationRenderer.render(renderer, aFragment(Location(myFixture.file.virtualFile, range, "sink", null, null)))

        verify(renderer).append("(1, 3) ", SimpleTextAttributes.GRAY_ATTRIBUTES)
        verify(renderer).append("sink", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    @Test
    fun `renders unknown coordinates when the location does not exist`() {
        val renderer = mock<TreeCellRenderer>()

        LocationRenderer.render(renderer, aFragment(Location(null, range, "sink", null, null)))

        verify(renderer).append("(-, -) ", SimpleTextAttributes.GRAY_ATTRIBUTES)
        verify(renderer).append(" (unreachable in local code)", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
    }

    private fun aFragment(location: Location): FragmentLocation {
        val flow = Flow(1, listOf(location))
        return flow.crossFileFlowFragments[0].locations[0]
    }
}
