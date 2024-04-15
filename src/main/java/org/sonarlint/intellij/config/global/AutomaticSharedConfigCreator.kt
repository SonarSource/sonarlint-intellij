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
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.ProgressRunner
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.jetbrains.rd.swing.textProperty
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.HyperlinkEvent
import org.sonarlint.intellij.actions.OpenInBrowserAction
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.finding.hotspot.SecurityHotspotsRefreshTrigger
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.util.ProgressUtils
import org.sonarlint.intellij.util.computeOnPooledThread
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth.HelpGenerateUserTokenResponse
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse

class AutomaticSharedConfigCreator(private val projectKey: String, private val serverUrl: String, isSQ: Boolean, project: Project) :
    DialogWrapper(false) {
    private var tokenValue: String? = null
    private val centerPanel: JBPanel<JBPanel<*>>
    private val createConnectionAction: DialogWrapperAction
    private val cancelConnectionAction: DialogWrapperAction
    private val warningIcon = JBLabel()
    private val redWarningIcon = JBLabel()
    private val warningLabel = SwingHelper.createHtmlViewer(false, null, null, JBUI.CurrentTheme.ContextHelp.FOREGROUND)
    private val connectionNameField = JBTextField()
    private val tokenField = JBPasswordField()
    private val connectedModeDescriptionLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val serverUrlLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val projectKeyLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val projectKeyField = JBTextField(projectKey).apply {
        isEditable = false
    }
    private val serverUrlField = JBTextField(serverUrl).apply {
        isEditable = false
    }
    private val connectionNameLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private val tokenLabel = SwingHelper.createHtmlViewer(false, null, null, null)
    private var serverConnection: ServerConnection? = null
    private val tokenGenerationButton = JButton("Generate Token")

    init {
        title = "Connect to This SonarQube Server?"
        val connectionNames = Settings.getGlobalSettings().serverNames
        connectionNameField.text = findFirstUniqueConnectionName(connectionNames, serverUrl)

        createConnectionAction = object : DialogWrapperAction("Connect To This SonarQube Server") {
            init {
                putValue(DEFAULT_ACTION, true)
                isEnabled = false
            }

            override fun doAction(e: ActionEvent) {
                serverConnection = ServerConnection.newBuilder().setHostUrl(serverUrl).setDisableNotifications(false).setToken(tokenValue)
                    .setName(connectionNameField.text).build().apply {
                        val globalSettings = Settings.getGlobalSettings()
                        Settings.getGlobalSettings().addServerConnection(this)
                        val serverChangeListener =
                            ApplicationManager.getApplication().messageBus.syncPublisher(GlobalConfigurationListener.TOPIC)
                        // notify in case the connections settings dialog is open to reflect the change
                        serverChangeListener.changed(globalSettings.serverConnections)
                    }

                val connection = Settings.getGlobalSettings().getServerConnectionByName(serverUrl)
                    .orElseThrow { IllegalStateException("Unable to find connection '$serverUrl'") }
                SonarLintUtils.getService(project, ProjectBindingManager::class.java).bindTo(connection, projectKey, emptyMap())
                SonarLintProjectNotifications.get(project).simpleNotification(
                    "Project successfully bound",
                    "Local project bound to project '$projectKey' of SonarQube server '${connection.name}'. " +
                        "You can now enjoy all capabilities of SonarLint Connected Mode. You can update the binding of this project in your SonarLint Settings.",
                    NotificationType.INFORMATION,
                    OpenInBrowserAction("Learn More in Documentation", null, SonarLintDocumentation.Intellij.CONNECTED_MODE_BENEFITS_LINK)
                )

                SonarLintUtils.getService(project, SecurityHotspotsRefreshTrigger::class.java).triggerRefresh()
                AssistBindingResponse(BackendService.projectId(project))
                close(OK_EXIT_CODE)
            }
        }

        cancelConnectionAction = object : DialogWrapperAction("Do Not Connect") {
            init {
                putValue(DEFAULT_ACTION, false)
            }

            override fun doAction(e: ActionEvent) {
                doCancelAction()
            }
        }

        centerPanel = JBPanel<JBPanel<*>>(GridBagLayout())

        warningIcon.setIconWithAlignment(AllIcons.General.InformationDialog, SwingConstants.TOP, SwingConstants.TOP)
        centerPanel.add(
            warningIcon, GridBagConstraints(
                0, 0, 1, 1, 0.0,
                0.0, GridBagConstraints.NORTH, GridBagConstraints.NONE, JBUI.insets(0, 10), 0, 18
            )
        )

        connectedModeDescriptionLabel.text = "Connecting SonarLint to SonarQube will enable issues " +
            "to be opened directly in your IDE and enable other <a href=\"${SonarLintDocumentation.Intellij.CONNECTED_MODE_BENEFITS_LINK}\">features and benefits</a>."
        connectedModeDescriptionLabel.addHyperlinkListener(object : HyperlinkAdapter() {
            override fun hyperlinkActivated(e: HyperlinkEvent) {
                BrowserUtil.browse(e.url)
            }
        })
        centerPanel.add(
            connectedModeDescriptionLabel, GridBagConstraints(
                1, 0, 1, 1, 1.0,
                0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(20), 0, 0
            )
        )

        serverUrlLabel.text = "Server URL"
        projectKeyLabel.text = "Project Key"
        centerPanel.add(
            projectKeyLabel, GridBagConstraints(
                1, 1, 1, 1, 1.0,
                0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0
            )
        )
        centerPanel.add(
            serverUrlLabel, GridBagConstraints(
                1, 3, 1, 1, 1.0,
                0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0
            )
        )

        val urlLabel = JBPanel<JBPanel<*>>(BorderLayout())
        urlLabel.add(serverUrlField, BorderLayout.CENTER)
        val projectKeyLabel = JBPanel<JBPanel<*>>(BorderLayout())
        projectKeyLabel.add(projectKeyField, BorderLayout.CENTER)
        centerPanel.add(
            projectKeyLabel, GridBagConstraints(
                1, 2, 1, 1, 1.0,
                0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0
            )
        )
        centerPanel.add(
            urlLabel, GridBagConstraints(
                1, 4, 1, 1, 1.0,
                0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0
            )
        )

        val warningPanel = JBPanel<JBPanel<*>>(BorderLayout())
        redWarningIcon.icon = AllIcons.Ide.FatalError
        warningLabel.text = "Always ensure that your Server URL matches your SonarQube instance. " +
            "Letting SonarLint connect to an untrusted SonarQube server is potentially dangerous."
        warningPanel.add(redWarningIcon, BorderLayout.WEST)
        warningPanel.add(warningLabel, BorderLayout.CENTER)
        centerPanel.add(warningPanel, GridBagConstraints(1, 5, 1, 1, 1.0,
            0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(30), 0, 0))

        connectionNameLabel.text = "Connection Name"
        centerPanel.add(
            connectionNameLabel, GridBagConstraints(
                1, 6, 1, 1, 1.0,
                0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0
            )
        )

        centerPanel.add(
            connectionNameField, GridBagConstraints(
                1, 7, 1, 1, 1.0,
                0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0
            )
        )

        tokenLabel.text = "Token"
        centerPanel.add(
            tokenLabel, GridBagConstraints(
                1, 8, 1, 1, 1.0,
                0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0
            )
        )

        val listener: DocumentListener = object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                createConnectionAction.isEnabled = tokenField.textProperty().value.isNotEmpty()
            }
        }
        tokenField.document.addDocumentListener(listener)

        centerPanel.add(
            tokenField, GridBagConstraints(
                1, 9, 1, 1, 1.0,
                0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insetsBottom(20), 0, 0
            )
        )

        centerPanel.add(
            tokenGenerationButton, GridBagConstraints(
                1, 10, 1, 1, 1.0,
                0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insetsBottom(20), 0, 0
            )
        )

        centerPanel.preferredSize = Dimension(600, 300)
        isResizable = false

        tokenGenerationButton.addActionListener { openTokenCreationPage(isSQ, serverUrl) }
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

    private fun openTokenCreationPage(isSQ: Boolean, serverUrl: String) {
        if (!BrowserUtil.isAbsoluteURL(serverUrl)) {
            Messages.showErrorDialog(centerPanel, "Can't launch browser for URL: $serverUrl", "Invalid Server URL")
            return
        }
        val progressWindow = ProgressWindow(true, false, null, centerPanel, "Cancel")
        progressWindow.title = ("Generating token...")

        try {
            val progressResult = ProgressRunner<HelpGenerateUserTokenResponse?> { pi: ProgressIndicator? ->
                computeOnPooledThread<HelpGenerateUserTokenResponse>(
                    "Generate User Token Task"
                ) {
                    val future =
                        SonarLintUtils.getService(
                            BackendService::class.java
                        ).helpGenerateUserToken(
                            serverUrl,
                            !isSQ
                        )
                    ProgressUtils.waitForFuture(
                        pi!!,
                        future
                    )
                }
            }
                .sync()
                .onThread(ProgressRunner.ThreadToUse.POOLED)
                .withProgress(progressWindow)
                .modal()
                .submitAndGet()
            val result = progressResult.result
            result?.let {
                val token = result.token
                token?.let {
                    tokenValue = it
                    tokenField.text = tokenValue
                }
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(centerPanel, e.message, "Unable to Generate Token")
        }
    }

}
