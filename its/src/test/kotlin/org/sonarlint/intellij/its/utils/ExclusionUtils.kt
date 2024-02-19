package org.sonarlint.intellij.its.utils

import com.intellij.remoterobot.fixtures.ActionButtonFixture
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.clickWhenEnabled
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.jbTextFieldsWithBrowseButton
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import java.time.Duration

class ExclusionUtils {

    companion object {

        fun excludeFile(fileName: String) {
            openExclusionsTab()
            excludeFileAndPressOk(fileName)
        }

        fun removeFileExclusion(fileName: String) {
            openExclusionsTab()
            removeSpecificExclusion(fileName)
        }

        private fun openExclusionsTab() {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Current File") { select() }
                        content("CurrentFilePanel") {
                            toolBarButton("Configure SonarLint").click()
                        }
                        selectExclusionTab()
                    }
                }
            }
        }

        private fun selectExclusionTab() {
            with(remoteRobot) {
                idea {
                    dialog("Project Settings") {
                        findText("File Exclusions").click()
                    }
                }
            }
        }

        private fun removeSpecificExclusion(fileName: String) {
            with(remoteRobot) {
                idea {
                    dialog("Project Settings") {
                        findText("File Exclusions").click()
                        findText(fileName).click()
                        actionButton(ActionButtonFixture.byTooltipText("Remove")).click()
                        button("OK").click()
                    }
                }
            }
        }

        private fun excludeFileAndPressOk(fileName: String) {
            with(remoteRobot) {
                idea {
                    dialog("Project Settings") {
                        findText("File Exclusions").click()
                        actionButton(ActionButtonFixture.byTooltipText("Add")).clickWhenEnabled()
                        dialog("Add SonarLint File Exclusion") {
                            jbTextFieldsWithBrowseButton()[0].click()
                            keyboard { enterText(fileName) }
                            waitFor(Duration.ofSeconds(1)) {
                                button("OK").isEnabled()
                            }
                            button("OK").click()
                        }
                        button("OK").click()
                    }
                }
            }
        }

    }

}