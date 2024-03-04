/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.config.global

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkEvent
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.CONNECTED_MODE_BENEFITS_LINK
import org.sonarlint.intellij.messages.GlobalConfigurationListener

class AutomaticServerConnectionCreator(private val serverUrl: String, private val tokenValue: String) :
    DialogWrapper(false) {

    private val centerPanel: JBPanel<JBPanel<*>>
    private val createConnectionAction: DialogWrapperAction
    private val cancelConnectionAction: DialogWrapperAction
    private val warningIcon = JBLabel()
    private val connectionNameField = JBTextField()
    private val connectedModeDescriptionLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val serverUrlLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val serverUrlField = JBTextField(serverUrl).apply {
        isEditable = false
    }
    private val redWarningIcon = JBLabel()
    private val warningLabel = SwingHelper.createHtmlViewer(false, null, null, JBUI.CurrentTheme.ContextHelp.FOREGROUND)
    private val connectionNameLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val tokenLabel = SwingHelper.createHtmlViewer(false, null, null, JBUI.CurrentTheme.ContextHelp.FOREGROUND)
    private var serverConnection: ServerConnection? = null
    private val proxyButton = JButton("Proxy")

    init {
        title = "Do You Trust This SonarQube Server?"
        val connectionNames = Settings.getGlobalSettings().serverNames
        connectionNameField.text = findFirstUniqueConnectionName(connectionNames, serverUrl)

        createConnectionAction = object : DialogWrapperAction("Connect To This SonarQube Server") {
            init {
                putValue(DEFAULT_ACTION, true)
            }

            override fun doAction(e: ActionEvent) {
                val globalSettings = Settings.getGlobalSettings()
                serverConnection = ServerConnection.newBuilder().setHostUrl(serverUrl).setDisableNotifications(false).setToken(tokenValue).setName(connectionNameField.text).build().apply {
                    Settings.getGlobalSettings().addServerConnection(this)
                    val serverChangeListener = ApplicationManager.getApplication().messageBus.syncPublisher(GlobalConfigurationListener.TOPIC)
                    // notify in case the connections settings dialog is open to reflect the change
                    serverChangeListener.changed(globalSettings.serverConnections)
                }
                close(OK_EXIT_CODE)
            }
        }

        cancelConnectionAction = object : DialogWrapperAction("I Don't Trust This Server") {
            init {
                putValue(DEFAULT_ACTION, false)
            }

            override fun doAction(e: ActionEvent) {
                doCancelAction()
            }
        }

        centerPanel = JBPanel<JBPanel<*>>(GridBagLayout())

        warningIcon.setIconWithAlignment(AllIcons.General.InformationDialog, SwingConstants.TOP, SwingConstants.TOP)
        centerPanel.add(warningIcon, GridBagConstraints(0, 0, 1, 1, 0.0,
            0.0, GridBagConstraints.NORTH, GridBagConstraints.NONE, JBUI.insets(0, 10), 0, 18))

        connectedModeDescriptionLabel.text = "Connecting SonarLint to SonarQube will enable issues " +
            "to be opened directly in your IDE and enable other <a href=\"$CONNECTED_MODE_BENEFITS_LINK\">features and benefits</a>."
        connectedModeDescriptionLabel.addHyperlinkListener(object : HyperlinkAdapter() {
            override fun hyperlinkActivated(e: HyperlinkEvent) {
                BrowserUtil.browse(e.url)
            }
        })
        centerPanel.add(connectedModeDescriptionLabel, GridBagConstraints(1, 0, 1, 1, 1.0,
            0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(20), 0, 0))

        serverUrlLabel.text = "Server URL"
        centerPanel.add(serverUrlLabel, GridBagConstraints(1, 1, 1, 1, 1.0,
            0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0))

        val urlProxyLabel = JBPanel<JBPanel<*>>(BorderLayout())
        urlProxyLabel.add(serverUrlField, BorderLayout.CENTER)
        urlProxyLabel.add(proxyButton, BorderLayout.EAST)
        centerPanel.add(urlProxyLabel, GridBagConstraints(1, 2, 1, 1, 1.0,
            0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0))

        val warningPanel = JBPanel<JBPanel<*>>(BorderLayout())
        redWarningIcon.icon = AllIcons.Ide.FatalError
        warningLabel.text = "Always ensure that your Server URL matches your SonarQube instance. " +
            "Letting SonarLint connect to an untrusted SonarQube server is potentially dangerous."
        warningPanel.add(redWarningIcon, BorderLayout.WEST)
        warningPanel.add(warningLabel, BorderLayout.CENTER)
        centerPanel.add(warningPanel, GridBagConstraints(1, 3, 1, 1, 1.0,
            0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(30), 0, 0))

        connectionNameLabel.text = "Connection Name"
        centerPanel.add(connectionNameLabel, GridBagConstraints(1, 4, 1, 1, 1.0,
            0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0))

        centerPanel.add(connectionNameField, GridBagConstraints(1, 5, 1, 1, 1.0,
            0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(20), 0, 0))

        tokenLabel.text = "A token will be automatically generated to allow access to your <u>SonarQube instance</u>."
        centerPanel.add(tokenLabel, GridBagConstraints(1, 6, 1, 1, 1.0,
            0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0))

        proxyButton.addActionListener { _ -> HttpConfigurable.editConfigurable(centerPanel) }

        centerPanel.preferredSize = Dimension(600, 300)
        isResizable = false

        init()
    }

    fun chooseResolution(): ServerConnection? {
        showAndGet()
        return serverConnection
    }

    override fun createCenterPanel() = centerPanel

    override fun createActions() = arrayOf(cancelConnectionAction, createConnectionAction)

    override fun getPreferredFocusedComponent() = getButton(createConnectionAction)

    private fun findFirstUniqueConnectionName(connectionNames: Set<String>, newConnectionName: String): String {
        var suffix = 1
        var uniqueName = newConnectionName
        while (connectionNames.contains(uniqueName)) {
            uniqueName = "$newConnectionName-$suffix"
            suffix++
        }
        return uniqueName
    }

}
