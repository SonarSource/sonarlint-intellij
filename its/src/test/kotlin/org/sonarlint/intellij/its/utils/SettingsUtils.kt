package org.sonarlint.intellij.its.utils

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ActionButtonFixture
import com.intellij.remoterobot.fixtures.JListFixture
import com.intellij.remoterobot.utils.waitFor
import org.assertj.swing.timing.Pause
import org.sonarlint.intellij.its.fixtures.IdeaFrame
import org.sonarlint.intellij.its.fixtures.PreferencesDialog
import org.sonarlint.intellij.its.fixtures.clickWhenEnabled
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.preferencesDialog
import org.sonarlint.intellij.its.fixtures.waitUntilLoaded
import org.sonarlint.intellij.its.fixtures.welcomeFrame
import java.time.Duration

class SettingsUtils {

    companion object {
        fun sonarLintGlobalSettings(remoteRobot: RemoteRobot, function: PreferencesDialog.() -> Unit) {
            settings(remoteRobot) {
                // let the dialog settle (if we type the search query too soon it might be cleared for no reason)
                Pause.pause(3000)

                // Search for SonarLint because sometimes it is off the screen
                search("SonarLint")

                tree {
                    waitUntilLoaded()
                    // little trick to check if the search has been applied
                    waitFor(Duration.ofSeconds(10), Duration.ofSeconds(1)) { collectRows().size in 1..10 }
                    Pause.pause(1000)
                    clickPath("Tools", "SonarLint")
                }

                // let the SonarLint view settle (sometimes the UI thread blocks for a few seconds)
                Pause.pause(4000)

                function(this)
            }
        }

        fun clearConnections(remoteRobot: RemoteRobot) {
            sonarLintGlobalSettings(remoteRobot) {
                val removeButton = actionButton(ActionButtonFixture.byTooltipText("Remove"))
                jList(JListFixture.byType()) {
                    while (collectItems().isNotEmpty()) {
                        removeButton.clickWhenEnabled()
                        optionalStep {
                            dialog("Connection In Use") {
                                button("Yes").click()
                            }
                        }
                    }
                }
                pressOk()
            }
        }

        fun clickPowerSaveMode(remoteRobot: RemoteRobot) {
            with(remoteRobot) {
                optionalIdeaFrame(this)?.apply {
                    actionMenu("File") {
                        open()
                        item("Power Save Mode") {
                            click()
                        }
                    }
                }
            }
        }

        fun goBackToWelcomeScreen(remoteRobot: RemoteRobot) {
            with(remoteRobot) {
                optionalIdeaFrame(this)?.apply {
                    actionMenu("File") {
                        open()
                        item("Close Project") {
                            click()
                        }
                    }
                }
            }
        }

        private fun optionalIdeaFrame(remoteRobot: RemoteRobot): IdeaFrame? {
            var ideaFrame: IdeaFrame? = null
            with(remoteRobot) {
                optionalStep {
                    // we might be on the welcome screen
                    ideaFrame = idea(Duration.ofSeconds(1))
                }
            }
            return ideaFrame
        }

        private fun settings(remoteRobot: RemoteRobot, function: PreferencesDialog.() -> Unit) {
            with(remoteRobot) {
                try {
                    welcomeFrame {
                        openPreferences()
                    }
                } catch (e: Exception) {
                    idea {
                        openSettings()
                    }
                }
                preferencesDialog {
                    function(this)
                }
            }
        }
    }

}
