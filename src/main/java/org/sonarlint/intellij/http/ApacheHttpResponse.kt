/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.http

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.sonarsource.sonarlint.core.commons.http.HttpClient
import java.io.ByteArrayInputStream

internal class ApacheHttpResponse(
  private val requestUrl: String, private val response: SimpleHttpResponse
) : HttpClient.Response {
  override fun code(): Int {
    return response.code
  }

  override fun bodyAsString(): String = response.bodyText

  override fun bodyAsStream() = ByteArrayInputStream(response.bodyBytes ?: ByteArray(0))

  override fun close() {
    // nothing to do
  }

  override fun url(): String {
    return requestUrl
  }
}
