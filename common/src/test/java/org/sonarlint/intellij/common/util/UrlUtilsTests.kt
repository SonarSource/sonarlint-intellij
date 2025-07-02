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
package org.sonarlint.intellij.common.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val BASE_URL = "http://localhost:8080/url"

class UrlUtilsTests {

    @Test
    fun `should not change url when null map`() {
        val same = UrlUtils.addParameters(BASE_URL, null)

        assertThat(same).isSameAs(BASE_URL)
    }

    @Test
    fun `should not change url when no params`() {
        val same = UrlUtils.addParameters(BASE_URL, emptyMap())

        assertThat(same).isSameAs(BASE_URL)
    }

    @Test
    fun `should add params where there are none`() {
        val withParameters = UrlUtils.addParameters(
            BASE_URL, mapOf(
                "param1" to "value1",
                "param2" to "value2"
            )
        )

        assertThat(withParameters).isEqualTo("$BASE_URL?param1=value1&param2=value2")
    }

    @Test
    fun `should preserve existing parameters`() {
        val withParameters = UrlUtils.addParameters(
            "$BASE_URL?existingParam=existingValue", mapOf(
                "param1" to "value1",
                "param2" to "value2"
            )
        )

        assertThat(withParameters).isEqualTo("$BASE_URL?existingParam=existingValue&param1=value1&param2=value2")
    }

    @Test
    fun `should append to existing parameters`() {
        val withParameters = UrlUtils.addParameters(
            "$BASE_URL?existingParam=existingValue", mapOf(
                "existingParam" to "value1",
                "param2" to "value2"
            )
        )

        assertThat(withParameters).isEqualTo("$BASE_URL?existingParam=existingValue&existingParam=value1&param2=value2")
    }
}
