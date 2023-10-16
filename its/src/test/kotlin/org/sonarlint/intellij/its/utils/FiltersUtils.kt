package org.sonarlint.intellij.its.utils

import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow

class FiltersUtils {

    companion object {
        fun setFocusOnNewCode() {
            with(BaseUiTest.remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Current File") { select() }
                        content("CurrentFilePanel") {
                            toolBarButton("Set Focus on New Code").click()
                        }
                    }
                }
            }
        }

        fun showResolvedIssues() {
            with(BaseUiTest.remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Current File") { select() }
                        content("CurrentFilePanel") {
                            toolBarButton("Include Resolved Issues").click()
                        }
                    }
                }
            }
        }
    }

}
