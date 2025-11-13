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

import org.sonarlint.intellij.common.util.UrlUtils.Companion.urlEncode

class UrlBuilder(private val path: String) {
    private val parameters = mutableListOf<QueryParam>()

    fun addParam(name: String, value: Int?): UrlBuilder {
        value?.let { parameters.add(QueryParam(name, listOf(it.toString()))) }
        return this
    }

    fun addParam(name: String, value: String?): UrlBuilder {
        value?.let { parameters.add(QueryParam(name, listOf(it))) }
        return this
    }

    fun addParam(name: String, value: Boolean?): UrlBuilder {
        value?.let { parameters.add(QueryParam(name, listOf(it.toString()))) }
        return this
    }

    fun addParam(name: String, values: List<String>?): UrlBuilder {
        if (!values.isNullOrEmpty()) {
            parameters.add(QueryParam(name, values))
        }
        return this
    }

    fun build(): String {
        val url = StringBuilder(path)
        var leadingCharacter = '?'
        for (parameter in parameters) {
            url.append(leadingCharacter)
                .append(urlEncode(parameter.name))
                .append("=")
                .append(parameter.values.joinToString(",") { urlEncode(it) })
            leadingCharacter = '&'
        }
        return url.toString()
    }

    private data class QueryParam(val name: String, val values: List<String>)
}
