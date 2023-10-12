package org.sonarlint.intellij.its.tests.domain

import com.intellij.remoterobot.RemoteRobot
import org.assertj.core.api.Assertions
import org.sonarlint.intellij.its.fixtures.closeAllGotItTooltips
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.notification
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.tests.AllUiTests.Companion.ISSUE_PROJECT_KEY
import org.sonarlint.intellij.its.tests.AllUiTests.Companion.ORCHESTRATOR
import org.sonarlint.intellij.its.tests.AllUiTests.Companion.token
import org.sonarlint.intellij.its.utils.ProjectBindingUtils.Companion.bindProjectToSonarQube

class CurrentFileTabTests {

    companion object {
        fun changeStatusAndPressChange(remoteRobot: RemoteRobot, status: String) {
            with(remoteRobot) {
                idea {
                    dialog("Mark Issue as Resolved on SonarQube") {
                        content(status) {
                            click()
                        }

                        pressButton("Mark Issue as...")
                    }
                }
            }
        }

        fun bindProjectFromCurrentFilePanel(remoteRobot: RemoteRobot) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tab("Current File") { select() }
                        content("CurrentFilePanel") {
                            toolBarButton("Configure SonarLint").click()
                        }
                    }
                    bindProjectToSonarQube(
                        remoteRobot,
                        ORCHESTRATOR.server.url,
                        token,
                        ISSUE_PROJECT_KEY
                    )
                }
            }
        }

        fun confirm(remoteRobot: RemoteRobot) {
            with(remoteRobot) {
                idea {
                    dialog("Confirm marking issue as resolved") {
                        pressButton("Confirm")
                    }
                }
            }
        }

        fun verifyIssueStatusWasSuccessfullyChanged(remoteRobot: RemoteRobot) {
            with(remoteRobot) {
                idea {
                    notification("The issue was successfully marked as resolved")
                    toolWindow("SonarLint") {
                        content("CurrentFilePanel") {
                            hasText("No issues found in the current opened file")
                        }
                    }
                }
            }
        }

        fun verifyIssueTreeContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Current File") { select() }
                        expectedMessages.forEach { Assertions.assertThat(hasText(it)).isTrue() }
                    }
                }
            }
        }

        fun openIssueReviewDialogFromList(remoteRobot: RemoteRobot, issueMessage: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        closeAllGotItTooltips()
                        tabTitleContains("Current File") { select() }
                        findText(issueMessage).rightClick()
                    }
                    actionMenuItem("Mark Issue as...") {
                        click()
                    }
                }
            }
        }
    }

}
