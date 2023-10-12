package org.sonarlint.intellij.its.tests.domain

import com.intellij.remoterobot.RemoteRobot
import org.assertj.core.api.Assertions
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.notification
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow

class SecurityHotspotTabTests {

    companion object {

        fun openSecurityHotspotReviewDialogFromList(remoteRobot: RemoteRobot, securityHotspotMessage: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Security Hotspots") { select() }
                        content("SecurityHotspotTree") {
                            findText(securityHotspotMessage).rightClick()
                        }
                    }
                    actionMenuItem("Review Security Hotspot") {
                        click()
                    }
                }
            }
        }

        fun changeSecurityHotspotStatusAndPressChange(remoteRobot: RemoteRobot, status: String) {
            with(remoteRobot) {
                idea {
                    dialog("Change Security Hotspot Status on SonarQube") {
                        content(status) {
                            click()
                        }
                        pressButton("Change Status")
                    }
                }
            }
        }

        fun verifySecurityHotspotStatusWasSuccessfullyChanged(remoteRobot: RemoteRobot) {
            with(remoteRobot) {
                idea {
                    notification("The Security Hotspot status was successfully updated")
                    toolWindow("SonarLint") {
                        content("SecurityHotspotsPanel") {
                            hasText("No Security Hotspot found.")
                        }
                    }
                }
            }
        }

        fun verifySecurityHotspotTabContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Security Hotspots") { select() }
                        content("SecurityHotspotsPanel") {
                            expectedMessages.forEach { Assertions.assertThat(hasText(it)).isTrue() }
                        }
                    }
                }
            }
        }

        fun verifySecurityHotspotTreeContainsMessages(remoteRobot: RemoteRobot, vararg expectedMessages: String) {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Security Hotspots") { select() }
                        content("SecurityHotspotTree") {
                            expectedMessages.forEach { Assertions.assertThat(hasText(it)).isTrue() }
                        }
                    }
                }
            }
        }
    }

}
