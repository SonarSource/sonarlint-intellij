/*
 * SonarLint for IntelliJ IDEA ITs
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
package org.sonarlint.intellij.its.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.assertj.swing.timing.Pause
import java.time.Duration

/**
 * Inspired from
 * https://github.com/aws/aws-toolkit-jetbrains/blob/081ba9b3e7f664bc2da6220ff8b313c9216745c7/ui-tests/tst/software/aws/toolkits/jetbrains/uitests/fixtures/JTreeFixture.kt
 */
class JTreeFixture(
  remoteRobot: RemoteRobot,
  remoteComponent: RemoteComponent
) : ComponentFixture(remoteRobot, remoteComponent) {
  var separator: String = "/"

  fun hasPath(vararg paths: String) = try {
    runJsPathMethod("node", *paths)
    true
  } catch (e: Exception) {
    false
  }

  fun clickPath(vararg paths: String) = runJsPathMethod("clickPath", *paths)
  fun expandPath(vararg paths: String) = runJsPathMethod("expandPath", *paths)
  fun rightClickPath(vararg paths: String) = runJsPathMethod("rightClickPath", *paths)
  fun doubleClickPath(vararg paths: String) = runJsPathMethod("doubleClickPath", *paths)

  fun requireSelection(vararg paths: String) {
    val path = paths.joinToString(separator)
    step("requireSelection $path") {
      runJs(
        """
                const jTreeFixture = JTreeFixture(robot, component);
                jTreeFixture.replaceSeparator('$separator')
                // Have to disambiguate int[] vs string[]
                jTreeFixture['requireSelection(java.lang.String[])'](['$path']) 
                """.trimIndent()
      )
    }
  }

  private fun runJsPathMethod(name: String, vararg paths: String) {
    val path = paths.joinToString(separator)
    step("$name $path") {
      runJs(
        """
                const jTreeFixture = JTreeFixture(robot, component);
                jTreeFixture.replaceSeparator('$separator')
                jTreeFixture.$name('$path') 
                """.trimIndent()
      )
    }
  }
}

fun JTreeFixture.waitUntilLoaded() {
  step("waiting for loading text to go away...") {
    Pause.pause(100)
    waitFor(duration = Duration.ofMinutes(1)) {
      // Do not use hasText(String) https://github.com/JetBrains/intellij-ui-test-robot/issues/10
      !hasText { txt -> txt.text == "loading..." }
    }
    Pause.pause(100)
  }
}
