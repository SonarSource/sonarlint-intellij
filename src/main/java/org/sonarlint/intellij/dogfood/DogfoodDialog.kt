package org.sonarlint.intellij.dogfood

import com.intellij.openapi.ui.DialogWrapper

class DogfoodDialog : DialogWrapper(false) {

    private val centerPanel = DogfoodPanel()

    fun setCredentials(): DogfoodCredentials? {
        title = "Insert Your Repox Credentials"
        isResizable = false
        init()

        val accepted = showAndGet()
        return if (accepted) DogfoodCredentials(centerPanel.getUsername(), centerPanel.getPassword()) else null
    }

    override fun createCenterPanel() = centerPanel

}