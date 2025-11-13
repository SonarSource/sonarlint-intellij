/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.credentials.CredentialsService
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.CONNECTED_MODE_BENEFITS_LINK
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.util.RegionUtils
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto

class AutomaticServerConnectionCreator(private val serverOrOrg: String, private val tokenValue: String,
                                       private val isSQ: Boolean, private val region: SonarCloudRegion?) :
    DialogWrapper(false) {

    private val centerPanel: JBPanel<JBPanel<*>>
    private val createConnectionAction: DialogWrapperAction
    private val cancelConnectionAction: DialogWrapperAction
    private val warningIcon = JBLabel()
    private val connectionNameField = JBTextField()
    private val scURLLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val scURLField = JBTextField().apply {
        isEditable = false
    }
    private val connectedModeDescriptionLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val serverUrlOrOrgLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val serverUrlOrOrgField = JBTextField(serverOrOrg).apply {
        isEditable = false
    }
    private val redWarningIcon = JBLabel()
    private val warningLabel = SwingHelper.createHtmlViewer(false, null, null, JBUI.CurrentTheme.ContextHelp.FOREGROUND)
    private val connectionNameLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val tokenLabel = SwingHelper.createHtmlViewer(false, null, null, JBUI.CurrentTheme.ContextHelp.FOREGROUND)
    private var serverConnection: ServerConnection? = null
    private val proxyButton = JButton("Proxy")

    init {
        title = if (isSQ) "Trust This SonarQube Server Instance?" else "Trust This SonarQube Cloud Organization?"
        val connectionNames = Settings.getGlobalSettings().serverNames
        connectionNameField.text = findFirstUniqueConnectionName(connectionNames, serverOrOrg)
        scURLField.text = if (!isSQ) RegionUtils.getUrlByRegion(region) else ""
        val buttonTextAction = if (isSQ) "Connect to This SonarQube Server Instance" else "Connect to This SonarQube Cloud Organization"

        createConnectionAction = object : DialogWrapperAction(buttonTextAction) {
            init {
                putValue(DEFAULT_ACTION, true)
            }

            override fun doAction(e: ActionEvent) {
                val globalSettings = Settings.getGlobalSettings()
                serverConnection = if (isSQ) {
                    ServerConnection.newBuilder().setHostUrl(serverOrOrg).setDisableNotifications(false)
                        .setName(connectionNameField.text).build()
                } else {
                    ServerConnection.newBuilder().setOrganizationKey(serverOrOrg).setDisableNotifications(false)
                        .setName(connectionNameField.text).setHostUrl(RegionUtils.getUrlByRegion(region))
                        .setRegion(region?.name ?: SonarCloudRegion.EU.name)
                        .build()
                }
                getService(CredentialsService::class.java).saveCredentials(
                    connectionNameField.text,
                    Either.forLeft(TokenDto(tokenValue))
                )
                serverConnection?.apply {
                    Settings.getGlobalSettings().addServerConnection(this)
                    val serverChangeListener =
                        ApplicationManager.getApplication().messageBus.syncPublisher(GlobalConfigurationListener.TOPIC)
                    // notify in case the connections settings dialog is open to reflect the change
                    serverChangeListener.changed(globalSettings.serverConnections)
                }
                close(OK_EXIT_CODE)
            }
        }

        val cancelTextButton = if (isSQ) "I Don't Trust This Server" else "I Don't Trust This Organization"
        cancelConnectionAction = object : DialogWrapperAction(cancelTextButton) {
            init {
                putValue(DEFAULT_ACTION, false)
            }

            override fun doAction(e: ActionEvent) {
                doCancelAction()
            }
        }

        var gridY = 0

        centerPanel = JBPanel<JBPanel<*>>(GridBagLayout())

        warningIcon.setIconWithAlignment(AllIcons.General.InformationDialog, SwingConstants.TOP, SwingConstants.TOP)
        centerPanel.add(
            warningIcon, GridBagConstraints(
                0, gridY, 1, 1, 0.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.NONE, JBUI.insets(0, 10), 0, 18
            )
        )

        connectedModeDescriptionLabel.text =
            "Connecting SonarQube for IDE to ${if (isSQ) "SonarQube Server" else "SonarQube Cloud"} will enable issues " +
                "to be opened directly in your IDE and enable other <a href=\"$CONNECTED_MODE_BENEFITS_LINK\">features and benefits</a>."
        connectedModeDescriptionLabel.addHyperlinkListener(object : HyperlinkAdapter() {
            override fun hyperlinkActivated(e: HyperlinkEvent) {
                BrowserUtil.browse(e.url)
            }
        })
        centerPanel.add(
            connectedModeDescriptionLabel, GridBagConstraints(
                1, gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(20), 0, 0
            )
        )

        serverUrlOrOrgLabel.text = if (isSQ) "Server URL" else "Organization Name"
        centerPanel.add(
            serverUrlOrOrgLabel, GridBagConstraints(
                1, ++gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0
            )
        )

        val urlProxyLabel = JBPanel<JBPanel<*>>(BorderLayout())
        urlProxyLabel.add(serverUrlOrOrgField, BorderLayout.CENTER)
        urlProxyLabel.add(proxyButton, BorderLayout.EAST)
        centerPanel.add(
            urlProxyLabel, GridBagConstraints(
                1, ++gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0
            )
        )

        val warningPanel = JBPanel<JBPanel<*>>(BorderLayout())
        redWarningIcon.icon = AllIcons.Ide.FatalError
        warningLabel.text = if (isSQ) {
            "Always ensure that your Server URL matches your SonarQube Server instance. " +
                "Letting SonarQube for IDE connect to an untrusted SonarQube Server instance is potentially dangerous."
        } else {
            "Ensure that the organization matches your SonarQube Cloud organization."
        }

        warningPanel.add(redWarningIcon, BorderLayout.WEST)
        warningPanel.add(warningLabel, BorderLayout.CENTER)
        centerPanel.add(
            warningPanel, GridBagConstraints(
                1, ++gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(30), 0, 0
            )
        )

        connectionNameLabel.text = "Connection Name"
        scURLLabel.text = "SonarQube Cloud URL"

        if (!isSQ && Settings.getGlobalSettings().isRegionEnabled) {
            centerPanel.add(
                scURLLabel, GridBagConstraints(
                    1, ++gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0
                )
            )
            centerPanel.add(
                scURLField, GridBagConstraints(
                    1, ++gridY, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(20), 0, 0
                )
            )
        }

        centerPanel.add(
            connectionNameLabel, GridBagConstraints(
                1, ++gridY,
                1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0
            )
        )

        centerPanel.add(
            connectionNameField, GridBagConstraints(
                1, ++gridY,
                1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(20), 0, 0
            )
        )

        tokenLabel.text =
            "A token will be automatically generated to allow access to your <u>${if (isSQ) "SonarQube Server instance" else "SonarQube Cloud organization"}</u>."
        centerPanel.add(
            tokenLabel, GridBagConstraints(
                1, ++gridY,
                1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0
            )
        )

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
        while (uniqueName in connectionNames) {
            uniqueName = "$newConnectionName-$suffix"
            suffix++
        }
        return uniqueName
    }

}
