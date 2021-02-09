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

import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.ParseException
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.serverapi.HttpClient
import java.io.IOException
import java.io.InputStream

private const val BODY_ERROR_MESSAGE = "Error reading body content"

internal class ApacheHttpResponse(
  private val requestUrl: String, private val response: ClassicHttpResponse
) : HttpClient.Response {
  override fun code(): Int {
    return response.code
  }

  override fun bodyAsString(): String {
    try {
      return EntityUtils.toString(response.entity)
    } catch (e: IOException) {
      throw RuntimeException(BODY_ERROR_MESSAGE, e)
    } catch (e: ParseException) {
      throw RuntimeException(BODY_ERROR_MESSAGE, e)
    }
  }

  override fun bodyAsStream(): InputStream {
    return try {
      response.entity.content
    } catch (e: IOException) {
      throw RuntimeException(BODY_ERROR_MESSAGE, e)
    }
  }

  override fun close() {
    try {
      response.close()
    } catch (e: IOException) {
      GlobalLogOutput.get().logError("Cannot close HttpClient", e)
    }
  }

  override fun url(): String {
    return requestUrl
  }
}