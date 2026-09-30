/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.its.driver

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.enabled
import com.intellij.driver.sdk.ui.haveText
import com.intellij.driver.sdk.ui.present
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.ui.components.dialog
import com.intellij.driver.sdk.ui.components.ideFrame
import com.intellij.driver.sdk.ui.components.settingsDialog
import com.intellij.driver.sdk.ui.components.showSettings
import com.intellij.driver.sdk.ui.components.toolWindow
import com.intellij.driver.sdk.ui.components.welcomeScreen
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import java.awt.Point
import java.awt.event.KeyEvent
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.sonarlint.intellij.its.utils.ProjectCopier

private const val SONARLINT_TOOL_WINDOW = "SonarQube for IDE"
private const val WALKTHROUGH_TOOL_WINDOW = "Welcome to SonarQube for IDE"

fun Driver.openExistingProject(projectName: String) {
    val projectPath = ProjectCopier.prepareProject(projectName)
    try {
        welcomeScreen {
            clickProjects()
            openProjectButton.click(Point(10, 10))
        }
    } catch (_: Exception) {
        invokeAction("OpenFile", false)
    }
    openProjectInFileBrowser(projectPath)
    waitForIndicators(5.minutes)
}

fun Driver.closeProject() {
    invokeAction("CloseProject", false)
    waitFor(duration = 2.minutes, errorMessage = "project to close") {
        var projectClosed = false
        ideFrame { projectClosed = project == null }
        projectClosed
    }
}

fun Driver.openProjectFile(relativePath: String) {
    openFile(relativePath, waitForCodeAnalysis = true)
    waitForIndicators(5.minutes)
}

fun Driver.openFileViaMenu(fileName: String) {
    invokeAction("GotoFile", false)
    ui.dialog(title = "Go to File") {
        keyboard {
            enterText(fileName)
            enter()
        }
    }
    waitForIndicators(5.minutes)
}

fun Driver.verifyCurrentFileTabContainsMessages(vararg expectedMessages: String) {
    waitForIndicators(5.minutes)
    expectedMessages.forEach { message ->
        sonarLintPanel("CurrentFilePanel", tabTitle = FINDINGS_TAB) {
            shouldBe("Expected '$message' in Current File tab", haveText(message), timeout = 5.minutes)
        }
    }
}

fun Driver.analyzeCurrentFileFromToolWindow() {
    sonarLintPanel("CurrentFilePanel", tabTitle = FINDINGS_TAB) {
        x(xQuery { byAccessibleName("Analyze Current File") }).click()
    }
    waitForIndicators(5.minutes)
}

fun Driver.analyzeAndVerifyReportTabContainsMessages(vararg expectedMessages: String) {
    invokeAction("SonarLint.AnalyzeAllFiles", false)
    waitForIndicators(5.minutes)
    sonarLintPanel("ReportPanel") {
        expectedMessages.forEach { message ->
            shouldBe("Expected '$message' in Report tab", haveText(message), timeout = 5.minutes)
        }
    }
}

fun Driver.toggleRule(ruleKey: String, ruleText: String) {
    ideFrame {
        showSettings()
        settingsDialog {
            Thread.sleep(3000)
            x(xQuery { byClass("SettingsSearch") }).keyboard { enterText("SonarQube for IDE") }
            Thread.sleep(1000)
            settingsTree.clickPath("Tools", "SonarQube for IDE")
            x(xQuery { byVisibleText("Rules") }).shouldBe("SonarQube for IDE Rules tab", present, timeout = 30.seconds).click()
            x(xQuery { byClass("SearchTextField") }).keyboard { enterText(ruleKey) }
            x(xQuery { byVisibleText(ruleText) }).shouldBe("Rule '$ruleText'", present, timeout = 30.seconds).click()
            Thread.sleep(1000)
            findText(ruleText).doubleClick()
            x(xQuery { byVisibleText("Apply") }).shouldBe("Apply to become enabled", enabled, timeout = 10.seconds).click()
            okButton.click()
        }
    }
}

fun Driver.setFocusOnNewCode() {
    toggleFocusOnNewCodeFilter()
}

fun Driver.resetFocusOnNewCode() {
    toggleFocusOnNewCodeFilter()
}

private fun Driver.toggleFocusOnNewCodeFilter() {
    sonarLintPanel("CurrentFilePanel", tabTitle = FINDINGS_TAB) {
        x(xQuery { byAccessibleName("Filter") }).click()
        x(xQuery { byTooltip("Focus on new code") }).click()
        x(xQuery { byAccessibleName("Filter") }).click()
    }
}

fun Driver.excludeFile(filePath: String) {
    sonarLintPanel("CurrentFilePanel", tabTitle = FINDINGS_TAB) {
        x(xQuery { byAccessibleName("Configure SonarQube for IDE") }).click()
    }
    ui.dialog(title = "Project Settings") {
        findText("File Exclusions").click()
        x(xQuery { byAccessibleName("Add") }).click()
        dialog(title = "Add SonarQube for IDE File Exclusion") {
            x(xQuery { byClass("TextFieldWithBrowseButton") }).click()
            keyboard { enterText(filePath) }
            x(xQuery { byVisibleText("OK") }).shouldBe("OK to become enabled", enabled, timeout = 5.seconds)
            pressButton("OK")
        }
        pressButton("OK")
    }
}

fun Driver.removeFileExclusion(filePath: String) {
    sonarLintPanel("CurrentFilePanel", tabTitle = FINDINGS_TAB) {
        x(xQuery { byAccessibleName("Configure SonarQube for IDE") }).click()
    }
    ui.dialog(title = "Project Settings") {
        findText("File Exclusions").click()
        findText(filePath).click()
        xx(xQuery { byClass("ActionButton") }).list()[1].click()
        pressButton("OK")
    }
}

fun Driver.closeWalkthrough() {
    ideFrame {
        toolWindow(WALKTHROUGH_TOOL_WINDOW) {
            findText("Next: Learn as You Code").click()
            keyboard { hotKey(KeyEvent.VK_SHIFT, KeyEvent.VK_ESCAPE) }
        }
    }
}

fun Driver.verifyWalkthroughIsNotShowing() {
    ideFrame {
        val walkthrough = x(xQuery {
            and(
                byClass("ToolWindowHeader"),
                contains(byVisibleText("Welcome to SonarQube for IDE"))
            )
        })
        if (walkthrough.present() && walkthrough.isVisible()) {
            throw AssertionError("Walkthrough is showing")
        }
    }
}

fun Driver.handleClionCppSetup() {
    ideFrame {
        optionalStep {
            findText("Load CMake project").click()
            driver.ui.dialog(title = "Trust CMake Project?") { pressButton("Trust Project") }
            driver.ui.dialog(title = "Open Project Wizard") { pressButton("OK") }
        }
        optionalStep {
            findText("Fix\u2026").click()
            x(xQuery { byClass("MyList") }).click(Point(10, 10))
        }
    }
    waitForIndicators(5.minutes)
}

private fun Driver.ensureSonarLintToolWindowVisible() {
    optionalStep { closeWalkthrough() }
    ideFrame {
        optionalStep {
            x(xQuery { byAccessibleName(SONARLINT_TOOL_WINDOW) }).click()
        }
    }
}

private const val FINDINGS_TAB = "Findings"

private fun Driver.sonarLintPanel(
    panelClass: String,
    tabTitle: String? = null,
    block: com.intellij.driver.sdk.ui.components.UiComponent.() -> Unit,
) {
    ensureSonarLintToolWindowVisible()
    ideFrame {
        if (tabTitle != null) {
            toolWindow(SONARLINT_TOOL_WINDOW) {
                x(xQuery { byVisibleText(tabTitle) })
                    .shouldBe("SonarQube for IDE tab '$tabTitle'", present, timeout = 1.minutes)
                    .click()
            }
        }
        x(xQuery { byClass(panelClass) })
            .shouldBe("SonarQube for IDE $panelClass", present, timeout = 5.minutes)
            .apply(block)
    }
}

private fun Driver.openProjectInFileBrowser(projectPath: java.nio.file.Path) {
    val dialogTitle = if (System.getProperty("its.ide.product") == "RD") "Select Path" else "Open File or Project"
    ui.dialog(title = dialogTitle) {
        val textField = x(xQuery { or(byClass("BorderlessTextField"), byClass("JTextField")) })
        val projectsDir = projectPath.parent.normalize().toString()
        textField.click()
        keyboard {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A)
            enterText(projectsDir)
        }
        x(xQuery { byAccessibleName("Refresh") }).click()
        Thread.sleep(2000)
        textField.click()
        keyboard {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A)
            enterText(projectPath.normalize().toString())
        }
        Thread.sleep(2000)
        pressButton("OK")
    }
}

private inline fun optionalStep(block: () -> Unit) {
    try {
        block()
    } catch (_: Exception) {
        // optional dialog or link not present on this IDE version
    }
}
