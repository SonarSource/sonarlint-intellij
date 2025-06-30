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

import io.ktor.http.URLBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class UrlUtils {
    companion object {
        @JvmStatic
        fun urlEncode(toEncode: String): String {
            return URLEncoder.encode(toEncode, StandardCharsets.UTF_8)
        }

        /**
         * Combines a URL string with additional parameters.
         *
         * If this method will ever be used with any outside input make sure to double-check that
         * no unsanitized parameters are passed here!
         */
        fun addParameters(url: String, nullableParams: Map<String, String>?): String {
            val params = nullableParams ?: mapOf()
            if (params.isEmpty()) {
                return url
            }

            val urlBuilder = URLBuilder(url)
            params.map {
                (key, value) -> urlBuilder.parameters.append(key, value)
            }
            return urlBuilder.build().toString()
        }
    }
}
