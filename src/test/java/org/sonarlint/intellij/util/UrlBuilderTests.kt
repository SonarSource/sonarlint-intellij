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
package org.sonarlint.intellij.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UrlBuilderTests {

    @Test
    fun `should build URL with no parameters`() {
        val url = UrlBuilder("/api/test").build()

        assertThat(url).isEqualTo("/api/test")
    }

    @Test
    fun `should build URL with single string parameter`() {
        val url = UrlBuilder("/api/test")
            .addParam("name", "value")
            .build()

        assertThat(url).isEqualTo("/api/test?name=value")
    }

    @Test
    fun `should build URL with single integer parameter`() {
        val url = UrlBuilder("/api/test")
            .addParam("page", 1)
            .build()

        assertThat(url).isEqualTo("/api/test?page=1")
    }

    @Test
    fun `should build URL with single boolean parameter`() {
        val url = UrlBuilder("/api/test")
            .addParam("enabled", true)
            .build()

        assertThat(url).isEqualTo("/api/test?enabled=true")
    }

    @Test
    fun `should build URL with multiple parameters`() {
        val url = UrlBuilder("/api/test")
            .addParam("name", "value")
            .addParam("page", 1)
            .addParam("enabled", true)
            .build()

        assertThat(url).isEqualTo("/api/test?name=value&page=1&enabled=true")
    }

    @Test
    fun `should build URL with list parameter`() {
        val url = UrlBuilder("/api/test")
            .addParam("tags", listOf("tag1", "tag2", "tag3"))
            .build()

        assertThat(url).isEqualTo("/api/test?tags=tag1,tag2,tag3")
    }

    @Test
    fun `should ignore null string parameter`() {
        val url = UrlBuilder("/api/test")
            .addParam("name", "value")
            .addParam("nullParam", null as String?)
            .build()

        assertThat(url).isEqualTo("/api/test?name=value")
    }

    @Test
    fun `should ignore null integer parameter`() {
        val url = UrlBuilder("/api/test")
            .addParam("name", "value")
            .addParam("nullParam", null as Int?)
            .build()

        assertThat(url).isEqualTo("/api/test?name=value")
    }

    @Test
    fun `should ignore null boolean parameter`() {
        val url = UrlBuilder("/api/test")
            .addParam("name", "value")
            .addParam("nullParam", null as Boolean?)
            .build()

        assertThat(url).isEqualTo("/api/test?name=value")
    }

    @Test
    fun `should ignore null list parameter`() {
        val url = UrlBuilder("/api/test")
            .addParam("name", "value")
            .addParam("nullParam", null as List<String>?)
            .build()

        assertThat(url).isEqualTo("/api/test?name=value")
    }

    @Test
    fun `should ignore empty list parameter`() {
        val url = UrlBuilder("/api/test")
            .addParam("name", "value")
            .addParam("emptyList", emptyList())
            .build()

        assertThat(url).isEqualTo("/api/test?name=value")
    }

    @Test
    fun `should URL encode special characters in parameter names`() {
        val url = UrlBuilder("/api/test")
            .addParam("user name", "value")
            .build()

        assertThat(url).isEqualTo("/api/test?user+name=value")
    }

    @Test
    fun `should URL encode special characters in parameter values`() {
        val url = UrlBuilder("/api/test")
            .addParam("name", "value with spaces")
            .build()

        assertThat(url).isEqualTo("/api/test?name=value+with+spaces")
    }

    @Test
    fun `should URL encode special characters in list parameter values`() {
        val url = UrlBuilder("/api/test")
            .addParam("tags", listOf("tag with spaces", "another tag"))
            .build()

        assertThat(url).isEqualTo("/api/test?tags=tag+with+spaces,another+tag")
    }

    @Test
    fun `should handle URL encoding of special characters`() {
        val url = UrlBuilder("/api/test")
            .addParam("query", "a&b=c")
            .addParam("path", "/some/path")
            .build()

        assertThat(url).isEqualTo("/api/test?query=a%26b%3Dc&path=%2Fsome%2Fpath")
    }

    @Test
    fun `should support fluent API chaining`() {
        val url = UrlBuilder("/api/test")
            .addParam("name", "value")
            .addParam("page", 1)
            .addParam("enabled", true)
            .addParam("tags", listOf("tag1", "tag2"))
            .build()

        assertThat(url).isEqualTo("/api/test?name=value&page=1&enabled=true&tags=tag1,tag2")
    }

    @Test
    fun `should handle zero integer value`() {
        val url = UrlBuilder("/api/test")
            .addParam("count", 0)
            .build()

        assertThat(url).isEqualTo("/api/test?count=0")
    }

    @Test
    fun `should handle false boolean value`() {
        val url = UrlBuilder("/api/test")
            .addParam("enabled", false)
            .build()

        assertThat(url).isEqualTo("/api/test?enabled=false")
    }

    @Test
    fun `should handle empty string value`() {
        val url = UrlBuilder("/api/test")
            .addParam("empty", "")
            .build()

        assertThat(url).isEqualTo("/api/test?empty=")
    }

    @Test
    fun `should handle single item list`() {
        val url = UrlBuilder("/api/test")
            .addParam("tags", listOf("single"))
            .build()

        assertThat(url).isEqualTo("/api/test?tags=single")
    }

    @Test
    fun `should handle complex URL with all parameter types`() {
        val url = UrlBuilder("/api/search")
            .addParam("query", "test query")
            .addParam("page", 2)
            .addParam("size", 10)
            .addParam("enabled", true)
            .addParam("filters", listOf("active", "verified"))
            .addParam("sort", "name")
            .build()

        assertThat(url).isEqualTo("/api/search?query=test+query&page=2&size=10&enabled=true&filters=active,verified&sort=name")
    }

} 
