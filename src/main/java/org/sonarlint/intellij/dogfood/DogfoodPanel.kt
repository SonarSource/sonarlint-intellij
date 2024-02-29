package org.sonarlint.intellij.dogfood

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBLabel
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.JTextField
import org.jdesktop.swingx.HorizontalLayout

class DogfoodPanel : JPanel() {

    private val usernameField = JTextField()
    private val passwordField = JTextField()

    init {
        layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 5, 15, true, false)

        val contextLabel = JBLabel("Credentials will be written in ~/.sonarlint/dogfood.properties")

        val usernamePanel = JPanel(HorizontalLayout(5))
        val usernameLabel = JBLabel("Username")
        usernamePanel.add(usernameLabel)
        usernamePanel.add(usernameField)
        usernameField.preferredSize = Dimension(400, usernameField.preferredSize.height)

        val passwordPanel = JPanel(HorizontalLayout(5))
        val passwordLabel = JBLabel("Password")
        passwordPanel.add(passwordLabel)
        passwordPanel.add(passwordField)
        passwordField.preferredSize = Dimension(400, passwordField.preferredSize.height)

        add(contextLabel)
        add(usernamePanel)
        add(passwordPanel)
    }

    fun getUsername(): String = usernameField.text

    fun getPassword(): String = passwordField.text

}