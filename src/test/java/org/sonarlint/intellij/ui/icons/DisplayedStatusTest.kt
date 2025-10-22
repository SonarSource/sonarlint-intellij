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
package org.sonarlint.intellij.ui.icons

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.sonarlint.intellij.finding.Finding

class DisplayedStatusTest {

    @Test
    fun `should be open for valid non-resolved findings`() {
        val finding = mockFinding(valid = true, resolved = false)

        val actual = DisplayedStatus.fromFinding(finding)

        assertThat(actual).isEqualTo(DisplayedStatus.OPEN)
    }

    @Test
    fun `should be accepted for valid resolved findings`() {
        val finding = mockFinding(valid = true, resolved = true)

        val actual = DisplayedStatus.fromFinding(finding)

        assertThat(actual).isEqualTo(DisplayedStatus.ACCEPTED)
    }

    @Test
    fun `should be invalid for non-valid findings`() {
        val finding = mockFinding(valid = false, resolved = true)

        val actual = DisplayedStatus.fromFinding(finding)

        assertThat(actual).isEqualTo(DisplayedStatus.INVALID)
    }

    private fun mockFinding(valid: Boolean, resolved: Boolean): Finding {
        val finding = mock(Finding::class.java)
        `when`(finding.isValid()).thenReturn(valid)
        `when`(finding.isResolved()).thenReturn(resolved)
        return finding
    }
}
