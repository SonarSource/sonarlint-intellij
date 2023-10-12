package org.sonarlint.intellij.its.utils

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.waitFor
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.editor
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.isCLion
import org.sonarlint.intellij.its.fixtures.openProjectFileBrowserDialog
import org.sonarlint.intellij.its.fixtures.welcomeFrame
import java.awt.Point
import java.io.File
import java.time.Duration

class OpeningUtils {

    companion object {
        fun openFile(remoteRobot: RemoteRobot, filePath: String, fileName: String = filePath) {
            with(remoteRobot) {
                idea {
                    runJs(
                        """
                        const file = component.project.getBaseDir().findFileByRelativePath("$filePath");
                        if (file) {
                            const openDescriptor = new com.intellij.openapi.fileEditor.OpenFileDescriptor(component.project, file);
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() => openDescriptor.navigate(true));
                        }
                        else {
                            throw "Cannot open file '" + $filePath +"': not found";
                        }
        """, false
                    )
                    waitFor(Duration.ofSeconds(10)) { editor(fileName).isShowing }
                    waitBackgroundTasksFinished()
                }
            }
        }

        fun openExistingProject(remoteRobot: RemoteRobot, projectName: String, isMaven: Boolean = false) {
            copyProjectFiles(projectName)
            with(remoteRobot) {
                welcomeFrame {
                    // Force the click on the left: https://github.com/JetBrains/intellij-ui-test-robot/issues/19
                    openProjectButton().click(Point(10, 10))
                }
                openProjectFileBrowserDialog {
                    selectProjectFile(projectName, isMaven)
                }
                if (!remoteRobot.isCLion()) {
                    optionalStep {
                        // from 2020.3.4+
                        dialog("Trust and Open Maven Project?", Duration.ofSeconds(5)) {
                            button("Trust Project").click()
                        }
                    }
                }
                idea {
                    waitBackgroundTasksFinished()
                }
                if (remoteRobot.isCLion()) {
                    optionalStep {
                        dialog("Open Project Wizard") {
                            button("OK").click()
                        }
                    }
                }
                idea {
                    // corresponding system property has been introduced around middle of 2020
                    // removable at some point when raising minimal version
                    closeTipOfTheDay()
                }
            }
        }

        private fun copyProjectFiles(projectName: String) {
            File("build/projects/$projectName").deleteRecursively()
            File("projects/$projectName").copyRecursively(File("build/projects/$projectName"))
        }
    }

}
