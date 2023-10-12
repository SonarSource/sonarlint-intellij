package org.sonarlint.intellij.its.utils

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.waitFor
import org.assertj.core.api.Assertions
import org.sonarlint.intellij.its.fixtures.closeAllGotItTooltips
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import java.time.Duration

class TabUtils {

    companion object {
        fun verifyCurrentFileTabContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Current File") { select() }
                        content("CurrentFilePanel") {
                            expectedMessages.forEach {
                                Assertions.assertThat(hasText(it)).`as`("Failed to find current file text '$it'").isTrue()
                            }
                        }
                    }
                }
            }
        }

        fun clickCurrentFileIssue(remoteRobot: RemoteRobot, issueMessage: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Current File") { select() }
                        closeAllGotItTooltips()
                        content("CurrentFilePanel") {
                            findText(issueMessage).click()
                        }
                    }
                }
            }
        }

        fun verifyRuleDescriptionTabContains(remoteRobot: RemoteRobot, expectedMessage: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        content("CurrentFilePanel") {
                            waitFor(Duration.ofSeconds(10), errorMessage = "Unable to find '$expectedMessage' in: ${findAllText()}") {
                                hasText(
                                    expectedMessage
                                )
                            }
                        }
                    }
                }
            }
        }

        fun verifyCurrentFileShowsCard(remoteRobot: RemoteRobot, expectedClass: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        Assertions.assertThat(findCard(expectedClass)).isNotNull
                    }
                }
            }
        }
    }

}
