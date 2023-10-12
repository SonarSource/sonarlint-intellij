package org.sonarlint.intellij.its.tests.domain

import com.intellij.remoterobot.RemoteRobot
import org.assertj.core.api.Assertions
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow

class ReportTabTests {

    companion object {
        fun verifyReportTabContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    analyzeFile()
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Report") { select() }
                        content("ReportPanel") {
                            expectedMessages.forEach { Assertions.assertThat(hasText(it)).isTrue() }
                        }
                    }
                }
            }
        }
    }

}
