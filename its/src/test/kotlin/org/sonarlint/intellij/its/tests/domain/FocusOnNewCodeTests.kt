package org.sonarlint.intellij.its.tests.domain

import com.intellij.remoterobot.RemoteRobot
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow

class FocusOnNewCodeTests {

    companion object {
        fun setFocusOnNewCode(remoteRobot: RemoteRobot) {
            with(remoteRobot) {
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
    }

}
