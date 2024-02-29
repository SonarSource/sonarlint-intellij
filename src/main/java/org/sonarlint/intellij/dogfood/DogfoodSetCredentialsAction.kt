package org.sonarlint.intellij.dogfood

import com.intellij.openapi.actionSystem.AnActionEvent
import java.io.PrintWriter
import java.nio.file.Paths
import org.sonarlint.intellij.actions.AbstractSonarAction
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread

class DogfoodSetCredentialsAction : AbstractSonarAction("Set Credentials") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runOnUiThread(project) {
            val credentials = DogfoodDialog().setCredentials()
            if (credentials != null) {
                val dogfoodPropertiesPath = getSonarlintSystemPath().resolve("dogfood.properties")
                val dogfoodPropertiesFile = dogfoodPropertiesPath.toFile()
                if (!dogfoodPropertiesFile.exists()) {
                    dogfoodPropertiesFile.createNewFile()
                }
                PrintWriter(dogfoodPropertiesFile).use {
                    it.println("username=${credentials.username}")
                    it.println("password=${credentials.password}")
                }
                loadCredentials()
                resetTries()
            }
        }
    }

    private fun getSonarlintSystemPath() = Paths.get(System.getProperty("user.home")).resolve(".sonarlint")

}