/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.its.tests

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Paths
import javax.imageio.ImageIO
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.verifyCurrentFileTabContainsMessages
import org.sonarlint.intellij.its.tests.domain.ReportTabTests.Companion.analyzeAndVerifyReportTabContainsMessages
import org.sonarlint.intellij.its.utils.ExclusionUtils.Companion.excludeFile
import org.sonarlint.intellij.its.utils.ExclusionUtils.Companion.removeFileExclusion
import org.sonarlint.intellij.its.utils.FiltersUtils.Companion.setFocusOnNewCode
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openExistingProject
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.openFile
import org.sonarlint.intellij.its.utils.SettingsUtils.Companion.toggleRule

@Tag("Standalone")
@EnabledIf("isIdeaCommunity")
class StandaloneIdeaTests : BaseUiTest() {

    @Test
    fun should_exclude_rule_and_focus_on_new_code() = uiTest {
        remoteRobot.getScreenshot()
        val screenshot = remoteRobot.getScreenshot()
        val cirrusWorkingDir = System.getenv("CIRRUS_WORKING_DIR")
        val ideaVersion = System.getenv("IDEA_VERSION")
        val outputFile = File("$cirrusWorkingDir/image1_$ideaVersion.jpg")
        ImageIO.write(screenshot, "png", outputFile)
        openExistingProject("sample-java-issues")
        openFile("src/main/java/foo/Foo.java", "Foo.java")
        toggleRule("java:S2094", "Classes should not be empty")
        verifyCurrentFileTabContainsMessages("No issues to display")
        toggleRule("java:S2094", "Classes should not be empty")
        setFocusOnNewCode()
        analyzeAndVerifyReportTabContainsMessages(
            "Found 1 new issue in 1 file from last 30 days",
            "No new Security Hotspots from last 30 days",
            "No older issues",
            "No older Security Hotspots"
        )
        verifyCurrentFileTabContainsMessages(
            "Found 1 new issue in 1 file from last 30 days",
            "No older issues",
        )
        verifyCurrentFileTabContainsMessages("Remove this empty class, write its code or make it an \"interface\".")
    }

    @Test
    fun should_exclude_file_and_analyze_file_and_no_issues_found() = uiTest {
        remoteRobot.getScreenshot()
        val screenshot = remoteRobot.getScreenshot()
        val cirrusWorkingDir = System.getenv("CIRRUS_WORKING_DIR")
        val ideaVersion = System.getenv("IDEA_VERSION")
        val outputFile = File("$cirrusWorkingDir/image2_$ideaVersion.jpg")
        ImageIO.write(screenshot, "png", outputFile)
        openExistingProject("sample-java-issues")
        excludeFile("src/main/java/foo/Foo.java")
        openFile("src/main/java/foo/Foo.java", "Foo.java")
        verifyCurrentFileTabContainsMessages("No analysis done on the current opened file")
        removeFileExclusion("src/main/java/foo/Foo.java")
    }

    @Test
    fun chart() = uiTest {
        remoteRobot.getScreenshot()
        val screenshot = remoteRobot.getScreenshot()
        val cirrusWorkingDir = System.getenv("CIRRUS_WORKING_DIR")
        val ideaVersion = System.getenv("IDEA_VERSION")
        val outputFile = File("$cirrusWorkingDir/image3_$ideaVersion.jpg")
        ImageIO.write(screenshot, "png", outputFile)
        openExistingProject("DuplicatedEnvsChart")
        openFile("templates/memory_limit_pod2.yml", "memory_limit_pod2.yml")
        verifyCurrentFileTabContainsMessages("Bind this resource's automounted service account to RBAC or disable automounting.")
    }

    fun executePS() {
        try {
            // Specify the PowerShell script to execute, located in the same directory as this Java file
            val scriptPath = Paths.get(System.getProperty("user.dir"), "src", "test", "kotlin", "org", "sonarlint", "intellij", "its", "tests", "foreground.ps1").toString()
            //val scriptPath = Paths.get(System.getProperty("user.dir"), "foreground.ps1").toString()

            // Create a ProcessBuilder to execute the PowerShell script
            val processBuilder = ProcessBuilder("powershell.exe", "-File", scriptPath)
            processBuilder.redirectErrorStream(true)

            // Start the process
            val process = processBuilder.start()

            // Read the output from the PowerShell script
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                println(line)
            }

            // Wait for the process to complete and get the exit value
            val exitCode = process.waitFor()
            println("Exited with code: $exitCode")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
