package org.sonarlint.intellij.its.tests.domain

import com.intellij.remoterobot.utils.waitFor
import java.time.Duration
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow

class DependencyRisksTabTests {

    companion object {

        fun verifyDependencyRisksTabContainsMessages(vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarQube for IDE") {
                        ensureOpen()
                        tabTitleContains("Dependency risks") { select() }
                        content("DependencyRiskPanel") {
                            // the synchronization can take a while to happen
                            expectedMessages.forEach {
                                waitFor(duration = Duration.ofSeconds(30)) {
                                    hasText(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
