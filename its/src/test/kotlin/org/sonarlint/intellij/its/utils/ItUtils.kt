/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.its.utils

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

object ItUtils {

  private var javaVersion: String
  val SONAR_VERSION: String = sonarVersion

  private val sonarVersion: String
    get() {
      val versionProperty = System.getProperty("sonar.runtimeVersion")
      return versionProperty ?: "LATEST_RELEASE"
    }

  init {
    if ("LATEST_RELEASE[6.7]" == System.getProperty("sonar.runtimeVersion")) {
      val props = Properties()
      try {
        Files.newBufferedReader(Paths.get("../../core/src/main/resources/plugins_min_versions.txt"), StandardCharsets.UTF_8).use { r ->
          props.load(r)
          javaVersion = props.getProperty("java")
        }
      } catch (e: IOException) {
        throw IllegalStateException(e)
      }
    } else {
      javaVersion = "LATEST_RELEASE"
    }
  }
}
